/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.parser.spi.meta;

import org.opendaylight.yangtools.yang.model.api.meta.DeclaredStatement;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;

public interface StatementFactory<A,D extends DeclaredStatement<A>,E extends EffectiveStatement<A, D>> {

    D createDeclared(StmtContext<A,D,?> ctx);

    E createEffective(StmtContext<A,D,E> ctx);

}
