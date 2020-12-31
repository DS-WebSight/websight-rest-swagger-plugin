package pl.ds.websight.openapi.model;

import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;

public class RestActionErrorResponse extends ApiResponse {

    public RestActionErrorResponse() {
        super();
        setDescription("Unexpected server error");
        setContent(new Content()
                .addMediaType("application/json", new MediaType()
                        .schema(new RestActionResultSchema("ERROR", null)))
        );
    }

}
