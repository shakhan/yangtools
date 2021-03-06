/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.parser.stmt.rfc6020;


import static org.opendaylight.yangtools.yang.parser.spi.meta.ModelProcessingPhase.SourceLinkage;
import static org.opendaylight.yangtools.yang.parser.spi.meta.StmtContextUtils.firstAttributeOf;

import com.google.common.base.Optional;
import java.net.URI;
import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.model.api.ModuleIdentifier;
import org.opendaylight.yangtools.yang.model.api.Rfc6020Mapping;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ImportStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.RevisionDateStatement;
import org.opendaylight.yangtools.yang.parser.builder.impl.ModuleIdentifierImpl;
import org.opendaylight.yangtools.yang.parser.spi.ModuleNamespace;
import org.opendaylight.yangtools.yang.parser.spi.meta.AbstractStatementSupport;
import org.opendaylight.yangtools.yang.parser.spi.meta.InferenceException;
import org.opendaylight.yangtools.yang.parser.spi.meta.ModelActionBuilder;
import org.opendaylight.yangtools.yang.parser.spi.meta.ModelActionBuilder.InferenceAction;
import org.opendaylight.yangtools.yang.parser.spi.meta.ModelActionBuilder.Prerequisite;
import org.opendaylight.yangtools.yang.parser.spi.meta.StmtContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.StmtContext.Mutable;
import org.opendaylight.yangtools.yang.parser.spi.source.SourceException;

public class ImportStatementDefinition extends
        AbstractStatementSupport<String, ImportStatement, EffectiveStatement<String, ImportStatement>> {

    public ImportStatementDefinition() {
        super(Rfc6020Mapping.Import);
    }

    @Override
    public String parseArgumentValue(StmtContext<?, ?, ?> ctx, String value) {
        return value;
    }

    @Override
    public ImportStatement createDeclared(StmtContext<String, ImportStatement, ?> ctx) {
        return new ImportStatementImpl(ctx);
    }

    @Override
    public EffectiveStatement<String, ImportStatement> createEffective(
            StmtContext<String, ImportStatement, EffectiveStatement<String, ImportStatement>> ctx) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onLinkageDeclared(final Mutable<String, ImportStatement, EffectiveStatement<String, ImportStatement>> stmt)
            throws InferenceException, SourceException {
        final ModuleIdentifier impIdentifier = getImportedModuleIdentifier(stmt);
        ModelActionBuilder importAction = stmt.newInferenceAction(SourceLinkage);
        final Prerequisite<StmtContext<?, ?, ?>> imported;
        final Prerequisite<Mutable<?, ?, ?>> linkageTarget;
        imported = importAction.requiresCtx(stmt, ModuleNamespace.class, impIdentifier, SourceLinkage);
        linkageTarget = importAction.mutatesCtx(stmt.getRoot(),SourceLinkage);

        importAction.apply(new InferenceAction() {

            @Override
            public void apply() throws InferenceException {
                StmtContext<?, ?, ?> importedModule = imported.get();
                // URI importedNs =
                // importedModule.firstSubstatement(NamespaceStatement.class).argument();
                // String prefix =
                // stmt.firstSubstatement(PrefixStatement.class).argument();
                linkageTarget.get().addToNs(ImportedModuleContext.class, impIdentifier, importedModule);
                // prefixTarget.get().addToNs(PrefixToModule.class, prefix,
                // QNameModule.create(importedNs, null));
            }

            @Override
            public void prerequisiteFailed(Collection<? extends Prerequisite<?>> failed) throws InferenceException {
                if (failed.contains(imported)) {
                    throw new InferenceException("Imported module was not found.", stmt.getStatementSourceReference());
                }
            }
        });
    }

    private static ModuleIdentifier getImportedModuleIdentifier(Mutable<String, ImportStatement, ?> stmt) throws SourceException {
        String moduleName = stmt.getStatementArgument();
        String revisionArg = firstAttributeOf(stmt.declaredSubstatements(), RevisionDateStatement.class);
        final Optional<Date> revision;
        if (revisionArg != null) {
            try {
                revision = Optional.of(SimpleDateFormatUtil.getRevisionFormat().parse(revisionArg));
            } catch (ParseException e) {
                throw new SourceException(
                        String.format("Revision value %s is not in required format yyyy-MM-dd", revisionArg),
                        stmt.getStatementSourceReference(), e);
            }
        } else {
            revision = Optional.absent();
        }
        return new ModuleIdentifierImpl(moduleName, Optional.<URI> absent(), revision);
    }

}