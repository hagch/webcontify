package io.webcontify.content.fieldtype;

import io.webcontify.content.definition.model.FieldConfig;
import io.webcontify.content.definition.model.FieldType;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class FieldTypeHandlerTest {

    @Test
    void interface_declares_fieldType_method() throws NoSuchMethodException {
        Method method = FieldTypeHandler.class.getMethod("fieldType");
        assertThat(method.getReturnType()).isEqualTo(FieldType.class);
    }

    @Test
    void interface_declares_validateConfig_method() throws NoSuchMethodException {
        Method method = FieldTypeHandler.class.getMethod("validateConfig", FieldConfig.class);
        assertThat(method.getReturnType()).isEqualTo(void.class);
    }

    @Test
    void interface_declares_cast_method() throws NoSuchMethodException {
        Method method = FieldTypeHandler.class.getMethod("cast", Object.class, FieldConfig.class);
        assertThat(method.getReturnType()).isEqualTo(Object.class);
    }

    @Test
    void interface_declares_validateValue_method() throws NoSuchMethodException {
        Method method = FieldTypeHandler.class.getMethod("validateValue", Object.class, FieldConfig.class);
        assertThat(method.getReturnType()).isEqualTo(void.class);
    }

    @Test
    void interface_declares_columnType_method() throws NoSuchMethodException {
        Method method = FieldTypeHandler.class.getMethod("columnType", FieldConfig.class);
        assertThat(method.getReturnType()).isEqualTo(DataType.class);
    }

    @Test
    void interface_declares_formatForDisplay_method() throws NoSuchMethodException {
        Method method = FieldTypeHandler.class.getMethod("formatForDisplay", Object.class, FieldConfig.class);
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void interface_declares_isVirtual_method() throws NoSuchMethodException {
        Method method = FieldTypeHandler.class.getMethod("isVirtual");
        assertThat(method.getReturnType()).isEqualTo(boolean.class);
    }

    @Test
    void interface_declares_supportedOperators_method() throws NoSuchMethodException {
        Method method = FieldTypeHandler.class.getMethod("supportedOperators");
        assertThat(method.getReturnType()).isEqualTo(Set.class);
    }

    @Test
    void interface_declares_buildCondition_method() throws NoSuchMethodException {
        Method method = FieldTypeHandler.class.getMethod("buildCondition", Field.class, Operator.class, Object.class, FieldConfig.class);
        assertThat(method.getReturnType()).isEqualTo(Condition.class);
    }

    @Test
    void operator_enum_contains_all_expected_values() {
        assertThat(Operator.values()).containsExactlyInAnyOrder(
                Operator.EQ, Operator.NEQ, Operator.GT, Operator.GTE,
                Operator.LT, Operator.LTE, Operator.LIKE, Operator.IN, Operator.ISNULL
        );
    }
}
