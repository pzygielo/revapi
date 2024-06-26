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
package org.revapi.simple;

import javax.annotation.Nullable;

import org.revapi.Element;
import org.revapi.ElementFilter;
import org.revapi.base.IndependentTreeFilter;

/**
 * @author Lukas Krejci
 *
 * @since 0.4.0
 *
 * @deprecated use {@link IndependentTreeFilter} or some other {@link org.revapi.TreeFilter} instead
 */
@Deprecated
public abstract class SimpleElementFilter extends SimpleConfigurable implements ElementFilter {
    @Override
    public boolean applies(@Nullable Element<?> element) {
        return false;
    }

    @Override
    public boolean shouldDescendInto(@Nullable Object element) {
        return false;
    }
}
