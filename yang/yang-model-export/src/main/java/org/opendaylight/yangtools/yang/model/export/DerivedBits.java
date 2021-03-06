/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.model.export;

import java.util.List;
import org.opendaylight.yangtools.yang.model.api.type.BitsTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.ExtendedType;

class DerivedBits extends NormalizatedDerivedType<BitsTypeDefinition> implements BitsTypeDefinition {

    public DerivedBits(final ExtendedType definition) {
        super(BitsTypeDefinition.class, definition);
    }

    @Override
    BitsTypeDefinition createDerived(final ExtendedType base) {
        return new DerivedBits(base);
    }

    @Override
    public List<Bit> getBits() {
        return getBaseType().getBits();
    }
}