/*
 * Copyright (c) 2017 Sergiy Yevtushenko
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

package org.rxbooter.injector.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.rxbooter.injector.annotations.ComputationStyle;
import org.rxbooter.injector.annotations.Singleton;
import org.rxbooter.injector.core.annotation.AnnotationFactory;
import org.rxbooter.injector.core.exception.InjectorException;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationFactoryTest {
    @Test
    public void shouldCreateAnnotationWithoutValues() throws Exception {
        Singleton singleton = AnnotationFactory.create(Singleton.class);

        assertThat(singleton).isInstanceOf(Singleton.class);
        assertThat(singleton.value()).isEqualTo(ComputationStyle.LAZY);
    }

    @Test
    public void shouldCreateAnnotationWithValues() throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("value", ComputationStyle.EAGER);

        Singleton singleton = AnnotationFactory.create(Singleton.class, values);

        assertThat(singleton).isInstanceOf(Singleton.class);
        assertThat(singleton.value()).isEqualTo(ComputationStyle.EAGER);
    }

    @Test
    public void shouldCalculateStringValue() throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("value", ComputationStyle.EAGER);

        Singleton singleton = AnnotationFactory.create(Singleton.class, values);

        assertThat(singleton).isInstanceOf(Singleton.class);
        assertThat(singleton.toString()).isEqualTo("@org.rxbooter.injector.annotations.Singleton(value=EAGER)");
    }

    @SuppressWarnings("SpellCheckingInspection")
    @Test(expected = InjectorException.class)
    public void shouldFailToCreateAnnotationWithIncorrectlyNamedValues() throws Exception {
        Map<String, Object> values = new HashMap<>();
        //noinspection SpellCheckingInspection
        values.put("valu", ComputationStyle.EAGER);

        Singleton singleton = AnnotationFactory.create(Singleton.class, values);
        assertThat(singleton).isInstanceOf(Singleton.class);
    }

    @Test(expected = InjectorException.class)
    public void shouldFailToCreateAnnotationWithIncorrectValueType() throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("value", 123);

        Singleton singleton = AnnotationFactory.create(Singleton.class, values);
        assertThat(singleton).isInstanceOf(Singleton.class);
    }

    @Test
    public void shouldBeEqualToRealAnnotation() throws Exception {
        Singleton singleton = AnnotationFactory.create(Singleton.class);
        Singleton real = TestAnnotation.class.getAnnotation(Singleton.class);

        assertThat(singleton).isInstanceOf(Singleton.class);
        assertThat(singleton).isEqualTo(real);
        assertThat(singleton.hashCode()).isEqualTo(real.hashCode());
    }

    @Test
    public void shouldBeEqualToRealAnnotationWithArrayValues() throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("value", new ElementType[] {ElementType.TYPE, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.METHOD});

        Target target = AnnotationFactory.create(Target.class, values);
        Target real = TestAnnotation.class.getAnnotation(Target.class);

        assertThat(target).isInstanceOf(Target.class);
        assertThat(target).isEqualTo(real);
        assertThat(target.hashCode()).isEqualTo(real.hashCode());
    }

    @Test(expected = InjectorException.class)
    public void shouldThrowExceptionIfMandatoryValueIsMissing() throws Exception {
        AnnotationFactory.create(Target.class);
    }

    @Singleton
    @Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.METHOD})
    public @interface TestAnnotation {
    }
}