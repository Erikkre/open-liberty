/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package web.dynamicannotationpure;

import javax.servlet.annotation.WebServlet;

import web.common.BaseServlet;

/**
 * Dynamic Annotation Pure Servlet
 */
//Follow constraints in dynamic annotations
//Include test to comment out RunAs here since predefining here not needed for Liberty
//@RunAs("Manager")
@WebServlet("/DynamicAnnotationPure3")
public class DynamicAnnotationPure3 extends BaseServlet {
    private static final long serialVersionUID = 1L;

    public DynamicAnnotationPure3() {
        super("DynamicAnnotationPure3");
    }

}
