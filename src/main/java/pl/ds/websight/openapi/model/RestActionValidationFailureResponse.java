package pl.ds.websight.openapi.model;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;

public class RestActionValidationFailureResponse extends ApiResponse {

    public RestActionValidationFailureResponse() {
        super();
        setDescription("Validation failure");
        setContent(new Content()
                .addMediaType("application/json", new MediaType()
                        .schema(new RestActionResultSchema("VALIDATION_FAILURE", new ArraySchema()
                                .items(new ObjectSchema()
                                        .addProperties("path", new StringSchema())
                                        .addProperties("invalidValue", new ObjectSchema())
                                        .addProperties("message", new StringSchema()))
                        )))
        );
    }

}
