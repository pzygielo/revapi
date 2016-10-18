/*
 * Copyright 2014 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi.java.spi;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

/**
 * All elements corresponding to various Java language model (apart from annotations (see
 * {@link org.revapi.java.spi.JavaAnnotationElement})), i.e. classes, methods, fields and method parameters, will
 * implement this interface.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface JavaModelElement extends JavaElement {

    @Override
    JavaModelElement getParent();

    /**
     * @return the corresponding {@link javax.lang.model.element.Element model element}.
     */
    TypeMirror getModelRepresentation();

    /**
     * The element that represents the declaration this model element.
     *
     * @return
     */
    Element getDeclaringElement();

    /**
     * Each {@link JavaTypeElement} contains as its children not only the elements that are declared on the type
     * but also elements that it inherits from its super types (with type parameters "instantiated" according to the
     * actual type).
     *
     * <p>This flag indicates if this is a child of type that is directly declared on it ({@code false}) or if it is
     * an instantiation of an inherited element ({@code true}).
     *
     * @return false if the parent type declares this child element, true if it is inherited from a super type
     */
    boolean isInherited();
}
