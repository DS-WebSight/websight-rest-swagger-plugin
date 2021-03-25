package pl.ds.websight.openapi;

import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.plugin.logging.Log;
import org.reflections.ReflectionUtils;
import pl.ds.websight.openapi.model.RestActionErrorResponse;
import pl.ds.websight.openapi.model.RestActionSuccessResponse;
import pl.ds.websight.openapi.model.RestActionValidationFailureResponse;
import pl.ds.websight.request.parameters.support.annotations.RequestParameter;
import pl.ds.websight.rest.framework.FreeFormResponse;
import pl.ds.websight.rest.framework.RestAction;
import pl.ds.websight.rest.framework.annotations.SlingAction;
import pl.ds.websight.rest.framework.annotations.SlingAction.HttpMethod;
import ru.vyarus.java.generics.resolver.GenericsResolver;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

class RestActionToOpenApiPathConverter {

    private static final String REST_ACTION_CLASS_SUFFIX = "RestAction";

    private static final Set<Class<? extends Annotation>> REQUIRED_REQUEST_PARAM_ANNOTATIONS =
            Stream.of(NotBlank.class, NotEmpty.class, NotNull.class).collect(toSet());

    private final Log log;
    private final String artifactId;

    RestActionToOpenApiPathConverter(Log log, String artifactId) {
        this.log = log;
        this.artifactId = artifactId;
    }

    Pair<String, PathItem> convert(Class<?> actionClass) {
        String actionPath = getRestActionPath(actionClass);
        if (actionPath == null) {
            return null;
        }
        List<Type> actionTypes = GenericsResolver.resolve(actionClass).type(RestAction.class).genericTypes();
        if (actionTypes.size() == 2) {
            Type modelType = actionTypes.get(0);
            Type responseType = actionTypes.get(1);
            log.info("\nProcessing action: " + actionClass.getName() + "\n" +
                    "  Request model type:   " + typeNameOrNone(modelType) + "\n" +
                    "  Response entity type: " + typeNameOrNone(responseType));
            HttpMethod method = actionClass.getAnnotation(SlingAction.class).value();
            switch (method) {
                case GET:
                    return Pair.of(actionPath, new PathItem().get(new Operation()
                            .parameters(buildGetParameters(modelType))
                            .responses(buildApiResponses(responseType))));
                case POST:
                    return Pair.of(actionPath, new PathItem().post(new Operation()
                            .requestBody(buildFormRequestBody(modelType))
                            .responses(buildApiResponses(responseType))));
                default:
                    log.warn("Unsupported method: " + method);
            }
        }
        return null;
    }

    private String getRestActionPath(Class<?> actionClass) {
        String simpleName = actionClass.getSimpleName();
        if (StringUtils.isBlank(simpleName) || !simpleName.contains(REST_ACTION_CLASS_SUFFIX)) {
            log.warn(REST_ACTION_CLASS_SUFFIX + " suffix missing in " + actionClass.getName() + ". Skipping class.");
            return null;
        }
        int restActionSuffixIndex = simpleName.lastIndexOf(REST_ACTION_CLASS_SUFFIX);
        simpleName = simpleName.substring(0, restActionSuffixIndex);
        String actionName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, simpleName);
        return "/apps/" + artifactId + "/bin/" + actionName + ".action";
    }

    private String typeNameOrNone(Type type) {
        return type != Void.class ? type.toString() : "<none>";
    }

    private List<Parameter> buildGetParameters(Type modelType) {
        List<Parameter> parameters = new ArrayList<>();
        for (Field modelField : getModelFields(modelType)) {
            Boolean required = isRequired(modelField);
            parameters.add(new PathParameter()
                    .in("query")
                    .name(getParameterName(modelField))
                    .required(required)
                    .schema(TypeUtil.javaTypeToSchema(modelField.getGenericType(), required)));
        }
        return parameters;
    }

    private ApiResponses buildApiResponses(Type responseType) {
        if (responseType == FreeFormResponse.class) {
            return new ApiResponses()
                    .addApiResponse("200", new ApiResponse().content(new Content().addMediaType("*/*",
                            new MediaType().schema(new BinarySchema()))));
            // TODO: not sure how to specify errors, because free form is unpredictable
        }
        return new ApiResponses()
                .addApiResponse("200", new RestActionSuccessResponse(TypeUtil.javaResponseTypeToSchema(null, responseType)))
                .addApiResponse("400", new RestActionValidationFailureResponse())
                .addApiResponse("500", new RestActionErrorResponse());
    }

    private RequestBody buildFormRequestBody(Type modelType) {
        return new RequestBody().content(new Content().addMediaType(
                "multipart/form-data", new MediaType().schema(modelTypeToFormParameters(modelType))));
    }

    private Schema<?> modelTypeToFormParameters(Type modelType) {
        ObjectSchema schema = new ObjectSchema();
        for (Field modelField : getModelFields(modelType)) {
            if (modelField.getType() == org.apache.sling.api.request.RequestParameter.class) {
                schema.addProperties(modelField.getName(), new BinarySchema());
            } else {
                Boolean required = isRequired(modelField);
                String parameterName = getParameterName(modelField);
                schema.addProperties(parameterName, TypeUtil.javaTypeToSchema(modelField.getGenericType(), required));
                if (Boolean.TRUE.equals(required)) {
                    schema.addRequiredItem(parameterName);
                }
            }
        }
        return schema;
    }

    @SuppressWarnings("unchecked")
    private List<Field> getModelFields(Type modelType) {
        if (modelType instanceof Class<?>) {
            return ReflectionUtils.getAllFields((Class<?>) modelType).stream()
                    .filter(field -> field.isAnnotationPresent(RequestParameter.class) ||
                            field.getType() == org.apache.sling.api.request.RequestParameter.class)
                    .collect(toList());
        }
        return Collections.emptyList();
    }

    private Boolean isRequired(Field modelField) {
        if (REQUIRED_REQUEST_PARAM_ANNOTATIONS.stream().anyMatch(modelField::isAnnotationPresent)) {
            return true;
        }
        return null; // NOSONAR: we don't want 'false' here to not serialize it to YAML result file
    }

    private String getParameterName(Field modelField) {
        return Optional.ofNullable(modelField.getAnnotation(RequestParameter.class))
                .map(RequestParameter::name)
                .filter(StringUtils::isNotBlank)
                .orElseGet(modelField::getName);
    }

}
