package pl.ds.websight.openapi.model;

import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

import java.util.Collections;

class RestActionResultSchema extends ObjectSchema {

    public RestActionResultSchema(String status, Schema<?> entitySchema) {
        super();
        if (entitySchema != null) {
            addProperties("entity", entitySchema);
        }
        addProperties("status", new StringSchema()._enum(Collections.singletonList(status)));
        addProperties("message", new StringSchema());
        addProperties("messageDetails", new StringSchema());
        addProperties("authContext", new ObjectSchema().addProperties("userId", new StringSchema()));
    }

}
