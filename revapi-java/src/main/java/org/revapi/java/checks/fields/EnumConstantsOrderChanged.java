/*
 * Copyright 2014-2023 Lukas Krejci
 * and other contributors as indicated by the @author tags.
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
 * limitations under the License.
 */
package org.revapi.java.checks.fields;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaFieldElement;

/**
 * @author Lukas Krejci
 *
 * @since 1.0
 */
public class EnumConstantsOrderChanged extends CheckBase {
    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.FIELD);
    }

    private boolean shouldCheck(JavaFieldElement oldField, JavaFieldElement newField) {
        return isBothAccessible(oldField, newField)
                && oldField.getDeclaringElement().getKind() == ElementKind.ENUM_CONSTANT
                && newField.getDeclaringElement().getKind() == ElementKind.ENUM_CONSTANT;
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    protected void doVisitField(@Nullable JavaFieldElement oldField, @Nullable JavaFieldElement newField) {
        if (!shouldCheck(oldField, newField)) {
            return;
        }

        Predicate<VariableElement> isNotEnumConstant = v -> v.getKind() != ElementKind.ENUM_CONSTANT;

        List<? extends VariableElement> fields = ElementFilter
                .fieldsIn(oldField.getDeclaringElement().getEnclosingElement().getEnclosedElements());
        fields.removeIf(isNotEnumConstant);

        int oldIdx = fields.indexOf(oldField.getDeclaringElement());

        fields = ElementFilter.fieldsIn(newField.getDeclaringElement().getEnclosingElement().getEnclosedElements());
        fields.removeIf(isNotEnumConstant);

        int newIdx = fields.indexOf(newField.getDeclaringElement());

        if (newIdx != oldIdx) {
            pushActive(oldField, newField, oldIdx, newIdx);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaFieldElement> fields = popIfActive();
        if (fields == null) {
            return null;
        }

        String oldIdx = fields.context[0].toString();
        String newIdx = fields.context[1].toString();

        return Collections.singletonList(createDifference(Code.FIELD_ENUM_CONSTANT_ORDER_CHANGED,
                Code.attachmentsFor(fields.oldElement, fields.newElement, "oldOrdinal", oldIdx, "newOrdinal", newIdx)));
    }
}
