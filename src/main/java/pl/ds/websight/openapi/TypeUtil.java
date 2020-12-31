package pl.ds.websight.openapi;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import pl.ds.websight.openapi.model.EnumSchema;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

class TypeUtil {

    private static final Set<Class<?>> JAVA_LANG_CLASSES = Stream.of(Object.class, Enum.class).collect(toSet());
    private static final Set<Class<?>> INTEGER_NUMBER_CLASSES = Stream.of(byte.class, short.class, int.class, long.class).collect(toSet());
    private static final Set<Class<?>> DECIMAL_NUMBER_CLASSES =
            Stream.of(Double.class, double.class, Float.class, float.class).collect(toSet());

    private TypeUtil() {
        // no instances
    }

    static Schema<?> javaTypeToSchema(Type type, Boolean required) {
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class && Collection.class.isAssignableFrom(((Class<?>) rawType))) {
                ArraySchema arraySchema = new ArraySchema();
                Type[] actualTypeArguments = ((ParameterizedType) type).getActualTypeArguments();
                if (actualTypeArguments.length == 1) {
                    arraySchema.setItems(javaTypeToSchema(actualTypeArguments[0], required));
                }
                return arraySchema;
            }
        } else if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            if (clazz == String.class || clazz == char.class) {
                return new StringSchema();
            } else if (clazz == Boolean.class || clazz == boolean.class) {
                return new BooleanSchema();
            } else if (clazz.isEnum()) {
                @SuppressWarnings("unchecked")
                Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) clazz;
                return new EnumSchema(enumClass);
            } else if (DECIMAL_NUMBER_CLASSES.contains(clazz)) {
                return new NumberSchema().nullable(required);
            } else if (Number.class.isAssignableFrom(clazz) || INTEGER_NUMBER_CLASSES.contains(clazz)) {
                return new IntegerSchema().nullable(required);
            } else if (Date.class.isAssignableFrom(clazz) || Calendar.class.isAssignableFrom(clazz)) {
                return new StringSchema();
            } else if (Collection.class.isAssignableFrom(clazz)) { // raw collection or list
                return new ArraySchema().items(new ObjectSchema());
            } else if (clazz.isArray()) {
                ArraySchema arraySchema = new ArraySchema();
                arraySchema.setItems(javaTypeToSchema(clazz.getComponentType(), required));
                return arraySchema;
            }
        }
        return new ObjectSchema();
    }

    static Schema<?> javaResponseTypeToSchema(Type containingClassType, Type type) {
        if (type == Void.class) {
            return null;
        }
        if (type instanceof ParameterizedType) {
            return parameterizedTypeToSchema(containingClassType, type);
        }
        if (type instanceof Class) {
            return rawClassToSchema(containingClassType, type);
        }
        return new ObjectSchema();
    }

    private static Schema<Object> parameterizedTypeToSchema(Type containingClassType, Type type) {
        Type rawType = ((ParameterizedType) type).getRawType();
        if (rawType instanceof Class) {
            Class<?> rawClass = (Class<?>) rawType;
            if (Collection.class.isAssignableFrom(rawClass)) {
                return collectionClassToSchema(containingClassType, type);
            } else if (Map.class.isAssignableFrom(rawClass)) {
                return mapClassToSchema(containingClassType, type);
            }
        }
        return new ObjectSchema();
    }

    private static ArraySchema collectionClassToSchema(Type containingClassType, Type type) {
        ArraySchema arraySchema = new ArraySchema();
        Type[] actualTypeArguments = ((ParameterizedType) type).getActualTypeArguments();
        if (actualTypeArguments.length == 1) {
            if (containingClassType == actualTypeArguments[0]) {
                // type embedded in itself, prevent recursion | TODO better idea? maybe components and $ref?
                arraySchema.setItems(new ObjectSchema());
            } else {
                arraySchema.setItems(javaResponseTypeToSchema(type, actualTypeArguments[0]));
            }
        }
        return arraySchema;
    }

    private static ObjectSchema mapClassToSchema(Type containingClassType, Type type) {
        ObjectSchema objectSchema = new ObjectSchema();
        Type[] actualTypeArguments = ((ParameterizedType) type).getActualTypeArguments();
        if (actualTypeArguments.length == 2) {
            if (containingClassType == actualTypeArguments[1]) {
                // type embedded in itself, prevent recursion
                objectSchema.additionalProperties(new ObjectSchema());
            } else {
                objectSchema.additionalProperties(javaResponseTypeToSchema(type, actualTypeArguments[1]));
            }
        }
        return objectSchema;
    }

    private static Schema<?> rawClassToSchema(Type containingClassType, Type type) {
        Class<?> clazz = (Class<?>) type;
        if (clazz == String.class || clazz == char.class) {
            return new StringSchema();
        } else if (clazz == Boolean.class || clazz == boolean.class) {
            return new BooleanSchema();
        } else if (clazz.isEnum()) {
            @SuppressWarnings("unchecked")
            Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) clazz;
            return new EnumSchema(enumClass);
        } else if (DECIMAL_NUMBER_CLASSES.contains(clazz)) {
            return new NumberSchema();
        } else if (Number.class.isAssignableFrom(clazz) || INTEGER_NUMBER_CLASSES.contains(clazz)) {
            return new IntegerSchema();
        } else if (Date.class.isAssignableFrom(clazz) || Calendar.class.isAssignableFrom(clazz)) {
            return new StringSchema();
        } else if (Collection.class.isAssignableFrom(clazz)) {
            // raw collection or list, parameterized is handled above
            return new ArraySchema().items(new ObjectSchema());
        } else if (clazz.isArray()) {
            return arrayTypeToSchema(containingClassType, type, clazz);
        } else if (Map.class.isAssignableFrom(clazz)) {
            return new ObjectSchema();
        } else {
            return customObjectToSchema(clazz);
        }
    }

    private static ArraySchema arrayTypeToSchema(Type containingClassType, Type type, Class<?> clazz) {
        ArraySchema arraySchema = new ArraySchema();
        Class<?> componentType = clazz.getComponentType();
        if (containingClassType == componentType) {
            // type embedded in itself, prevent recursion
            arraySchema.setItems(new ObjectSchema());
        } else {
            arraySchema.setItems(javaResponseTypeToSchema(type, componentType));
        }
        return arraySchema;
    }

    private static ObjectSchema customObjectToSchema(Class<?> clazz) {
        ObjectSchema schema = new ObjectSchema();
        try {
            for (PropertyDescriptor property : Introspector.getBeanInfo(clazz).getPropertyDescriptors()) {
                Method readMethod = property.getReadMethod();
                if (JAVA_LANG_CLASSES.contains(readMethod.getDeclaringClass())) {
                    continue;
                }
                schema.addProperties(property.getName(), javaResponseTypeToSchema(clazz, readMethod.getGenericReturnType()));
            }
        } catch (IntrospectionException e) {
            e.printStackTrace();
        }
        return schema;
    }

}
