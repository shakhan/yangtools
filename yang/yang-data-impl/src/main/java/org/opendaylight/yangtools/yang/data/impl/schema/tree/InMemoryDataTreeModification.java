/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.data.impl.schema.tree;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodes;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeModification;
import org.opendaylight.yangtools.yang.data.api.schema.tree.StoreTreeNodes;
import org.opendaylight.yangtools.yang.data.api.schema.tree.spi.TreeNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.spi.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class InMemoryDataTreeModification implements DataTreeModification {
    private static final AtomicIntegerFieldUpdater<InMemoryDataTreeModification> UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(InMemoryDataTreeModification.class, "sealed");
    private static final Logger LOG = LoggerFactory.getLogger(InMemoryDataTreeModification.class);

    private final RootModificationApplyOperation strategyTree;
    private final InMemoryDataTreeSnapshot snapshot;
    private final ModifiedNode rootNode;
    private final Version version;

    private volatile int sealed = 0;

    InMemoryDataTreeModification(final InMemoryDataTreeSnapshot snapshot, final RootModificationApplyOperation resolver) {
        this.snapshot = Preconditions.checkNotNull(snapshot);
        this.strategyTree = Preconditions.checkNotNull(resolver).snapshot();
        this.rootNode = ModifiedNode.createUnmodified(snapshot.getRootNode(), strategyTree.getChildPolicy());

        /*
         * We could allocate version beforehand, since Version contract
         * states two allocated version must be always different.
         *
         * Preallocating version simplifies scenarios such as
         * chaining of modifications, since version for particular
         * node in modification and in data tree (if successfully
         * committed) will be same and will not change.
         */
        this.version = snapshot.getRootNode().getSubtreeVersion().next();
    }

    ModifiedNode getRootModification() {
        return rootNode;
    }

    ModificationApplyOperation getStrategy() {
        return strategyTree;
    }

    @Override
    public void write(final YangInstanceIdentifier path, final NormalizedNode<?, ?> data) {
        checkSealed();

        resolveModificationFor(path).write(data);
    }

    @Override
    public void merge(final YangInstanceIdentifier path, final NormalizedNode<?, ?> data) {
        checkSealed();

        resolveModificationFor(path).merge(data);
    }

    @Override
    public void delete(final YangInstanceIdentifier path) {
        checkSealed();

        resolveModificationFor(path).delete();
    }

    @Override
    public Optional<NormalizedNode<?, ?>> readNode(final YangInstanceIdentifier path) {
        /*
         * Walk the tree from the top, looking for the first node between root and
         * the requested path which has been modified. If no such node exists,
         * we use the node itself.
         */
        final Entry<YangInstanceIdentifier, ModifiedNode> entry = StoreTreeNodes.findClosestsOrFirstMatch(rootNode, path, ModifiedNode.IS_TERMINAL_PREDICATE);
        final YangInstanceIdentifier key = entry.getKey();
        final ModifiedNode mod = entry.getValue();

        final Optional<TreeNode> result = resolveSnapshot(key, mod);
        if (result.isPresent()) {
            NormalizedNode<?, ?> data = result.get().getData();
            return NormalizedNodes.findNode(key, data, path);
        } else {
            return Optional.absent();
        }
    }

    private Optional<TreeNode> resolveSnapshot(final YangInstanceIdentifier path, final ModifiedNode modification) {
        final Optional<TreeNode> potentialSnapshot = modification.getSnapshot();
        if (potentialSnapshot != null) {
            return potentialSnapshot;
        }

        try {
            return resolveModificationStrategy(path).apply(modification, modification.getOriginal(),
                    version);
        } catch (Exception e) {
            LOG.error("Could not create snapshot for {}:{}", path, modification, e);
            throw e;
        }
    }

    private void upgradeIfPossible() {
        if (rootNode.getOperation() == LogicalOperation.NONE) {
            strategyTree.upgradeIfPossible();
        }
    }

    private ModificationApplyOperation resolveModificationStrategy(final YangInstanceIdentifier path) {
        LOG.trace("Resolving modification apply strategy for {}", path);

        upgradeIfPossible();
        return StoreTreeNodes.<ModificationApplyOperation>findNodeChecked(strategyTree, path);
    }

    private OperationWithModification resolveModificationFor(final YangInstanceIdentifier path) {
        upgradeIfPossible();

        /*
         * Walk the strategy and modification trees in-sync, creating modification nodes as needed.
         *
         * If the user has provided wrong input, we may end up with a bunch of TOUCH nodes present
         * ending with an empty one, as we will throw the exception below. This fact could end up
         * being a problem, as we'd have bunch of phantom operations.
         *
         * That is fine, as we will prune any empty TOUCH nodes in the last phase of the ready
         * process.
         */
        ModificationApplyOperation operation = strategyTree;
        ModifiedNode modification = rootNode;

        int i = 1;
        for(PathArgument pathArg : path.getPathArguments()) {
            Optional<ModificationApplyOperation> potential = operation.getChild(pathArg);
            if (!potential.isPresent()) {
                throw new IllegalArgumentException(String.format("Child %s is not present in tree.",
                        Iterables.toString(Iterables.limit(path.getPathArguments(), i))));
            }
            operation = potential.get();
            ++i;

            modification = modification.modifyChild(pathArg, operation.getChildPolicy());
        }

        return OperationWithModification.from(operation, modification);
    }

    @Override
    public void ready() {
        final boolean wasRunning = UPDATER.compareAndSet(this, 0, 1);
        Preconditions.checkState(wasRunning, "Attempted to seal an already-sealed Data Tree.");

        rootNode.seal();
    }

    private void checkSealed() {
        Preconditions.checkState(sealed == 0, "Data Tree is sealed. No further modifications allowed.");
    }

    @Override
    public String toString() {
        return "MutableDataTree [modification=" + rootNode + "]";
    }

    @Override
    public DataTreeModification newModification() {
        Preconditions.checkState(sealed == 1, "Attempted to chain on an unsealed modification");

        if (rootNode.getOperation() == LogicalOperation.NONE) {
            // Simple fast case: just use the underlying modification
            return snapshot.newModification();
        }

        /*
         * We will use preallocated version, this means returned snapshot will
         * have same version each time this method is called.
         */
        TreeNode originalSnapshotRoot = snapshot.getRootNode();
        Optional<TreeNode> tempRoot = strategyTree.apply(rootNode, Optional.of(originalSnapshotRoot), version);

        InMemoryDataTreeSnapshot tempTree = new InMemoryDataTreeSnapshot(snapshot.getSchemaContext(), tempRoot.get(), strategyTree);
        return tempTree.newModification();
    }

    Version getVersion() {
        return version;
    }
}
