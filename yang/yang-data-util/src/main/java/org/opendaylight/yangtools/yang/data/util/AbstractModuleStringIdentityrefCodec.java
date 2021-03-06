/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.data.util;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.Module;

@Beta
public abstract class AbstractModuleStringIdentityrefCodec extends AbstractStringIdentityrefCodec {
    /**
     * Resolve a string prefix into the corresponding module.
     *
     * @param prefix
     * @return module mapped to prefix, or null if the module cannot be resolved
     */
    protected abstract Module moduleForPrefix(@Nonnull String prefix);

    @Override
    protected final QName createQName(final String prefix, final String localName) {
        final Module module = moduleForPrefix(prefix);
        Preconditions.checkArgument(module != null, "Failed to lookup prefix %s", prefix);
        return QName.create(module.getQNameModule(), localName);
    }
}
