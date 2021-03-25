package pl.ds.websight.openapi.model;

import com.google.common.collect.Sets;
import io.swagger.v3.oas.models.media.StringSchema;

import java.util.Arrays;
import java.util.Set;

import static java.util.stream.Collectors.toList;

public class EnumSchema extends StringSchema {

    public EnumSchema(Class<? extends Enum<?>> enumClass, Enum<?>... excludedEnumValues) {
        super();
        Set<Enum<?>> excludedValues = Sets.newHashSet(excludedEnumValues);
        _enum(Arrays.stream(enumClass.getEnumConstants())
                .filter(value -> !excludedValues.contains(value))
                .map(Enum::name)
                .collect(toList()));
    }

    public EnumSchema(String... values) {
        super();
        _enum(Arrays.asList(values));
    }

}
