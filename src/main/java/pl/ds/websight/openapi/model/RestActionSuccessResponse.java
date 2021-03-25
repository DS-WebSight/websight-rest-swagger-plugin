package pl.ds.websight.openapi.model;

import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

public class RestActionSuccessResponse extends ApiResponse {

    public RestActionSuccessResponse(Schema<?> entitySchema) {
        super();
        setDescription("OK");
        setContent(new Content()
                .addMediaType("application/json", new MediaType()
                        .schema(new RestActionResultSchema("SUCCESS", entitySchema)))
        );
    }

}
