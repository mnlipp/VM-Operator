/*
 * VM-Operator
 * Copyright (C) 2023,2024 Michael N. Lipp
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.jdrupes.vmoperator.common;

import com.google.gson.JsonObject;
import io.kubernetes.client.Discovery;
import io.kubernetes.client.Discovery.APIResource;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.common.KubernetesType;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.EventsV1Api;
import io.kubernetes.client.openapi.models.EventsV1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.util.Strings;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

// TODO: Auto-generated Javadoc
/**
 * Helpers for K8s API.
 */
@SuppressWarnings({ "PMD.ShortClassName", "PMD.UseUtilityClass",
    "PMD.DataflowAnomalyAnalysis" })
public class K8s {

    /**
     * Returns the result from an API call as {@link Optional} if the
     * call was successful. Returns an empty `Optional` if the status
     * code is 404 (not found). Else throws an exception.
     *
     * @param <T> the generic type
     * @param response the response
     * @return the optional
     * @throws ApiException the API exception
     */
    public static <T extends KubernetesType> Optional<T>
            optional(KubernetesApiResponse<T> response) throws ApiException {
        if (response.isSuccess()) {
            return Optional.of(response.getObject());
        }
        if (response.getHttpStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            return Optional.empty();
        }
        response.throwsApiException();
        // Never reached
        return Optional.empty();
    }

    /**
     * Returns a new context with the given version as preferred version.
     *
     * @param context the context
     * @param version the version
     * @return the API resource
     */
    public static APIResource preferred(APIResource context, String version) {
        assert context.getVersions().contains(version);
        return new APIResource(context.getGroup(),
            context.getVersions(), version, context.getKind(),
            context.getNamespaced(), context.getResourcePlural(),
            context.getResourceSingular());
    }

    /**
     * Return a string representation of the context (API resource).
     *
     * @param context the context
     * @return the string
     */
    @SuppressWarnings("PMD.UseLocaleWithCaseConversions")
    public static String toString(APIResource context) {
        return (Strings.isNullOrEmpty(context.getGroup()) ? ""
            : context.getGroup() + "/")
            + context.getPreferredVersion().toUpperCase()
            + context.getKind();
    }

    /**
     * Convert Yaml to Json.
     *
     * @param client the client
     * @param yaml the yaml
     * @return the json element
     */
    public static JsonObject yamlToJson(ApiClient client, Reader yaml) {
        // Avoid Yaml.load due to
        // https://github.com/kubernetes-client/java/issues/2741
        @SuppressWarnings("PMD.UseConcurrentHashMap")
        Map<String, Object> yamlData
            = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);

        // There's no short-cut from Java (collections) to Gson
        var gson = client.getJSON().getGson();
        var jsonText = gson.toJson(yamlData);
        return gson.fromJson(jsonText, JsonObject.class);
    }

    /**
     * Lookup the specified API resource. If the version is `null` or
     * empty, the preferred version in the result is the default
     * returned from the server.
     *
     * @param client the client
     * @param group the group
     * @param version the version
     * @param kind the kind
     * @return the optional
     * @throws ApiException the api exception
     */
    public static Optional<APIResource> context(ApiClient client,
            String group, String version, String kind) throws ApiException {
        var apiMatch = new Discovery(client).findAll().stream()
            .filter(r -> r.getGroup().equals(group) && r.getKind().equals(kind)
                && (Strings.isNullOrEmpty(version)
                    || r.getVersions().contains(version)))
            .findFirst();
        if (apiMatch.isEmpty()) {
            return Optional.empty();
        }
        var apiRes = apiMatch.get();
        if (!Strings.isNullOrEmpty(version)) {
            if (!apiRes.getVersions().contains(version)) {
                return Optional.empty();
            }
            apiRes = new APIResource(apiRes.getGroup(), apiRes.getVersions(),
                version, apiRes.getKind(), apiRes.getNamespaced(),
                apiRes.getResourcePlural(), apiRes.getResourceSingular());
        }
        return Optional.of(apiRes);
    }

    /**
     * Get an object from its metadata.
     *
     * @param <T> the generic type
     * @param <LT> the generic type
     * @param api the api
     * @param meta the meta
     * @return the object
     */
    @Deprecated
    public static <T extends KubernetesObject, LT extends KubernetesListObject>
            Optional<T>
            get(GenericKubernetesApi<T, LT> api, V1ObjectMeta meta) {
        var response = api.get(meta.getNamespace(), meta.getName());
        if (response.isSuccess()) {
            return Optional.of(response.getObject());
        }
        return Optional.empty();
    }

    /**
     * Apply the given patch data.
     *
     * @param <T> the generic type
     * @param <LT> the generic type
     * @param api the api
     * @param existing the existing
     * @param update the update
     * @return the t
     * @throws ApiException the api exception
     */
    public static <T extends KubernetesObject, LT extends KubernetesListObject>
            T apply(GenericKubernetesApi<T, LT> api, T existing, String update)
                    throws ApiException {
        PatchOptions opts = new PatchOptions();
        opts.setForce(true);
        opts.setFieldManager("kubernetes-java-kubectl-apply");
        var response = api.patch(existing.getMetadata().getNamespace(),
            existing.getMetadata().getName(), V1Patch.PATCH_FORMAT_APPLY_YAML,
            new V1Patch(update), opts).throwsApiException();
        return response.getObject();
    }

    /**
     * Create an object reference.
     *
     * @param object the object
     * @return the v 1 object reference
     */
    public static V1ObjectReference
            objectReference(KubernetesObject object) {
        return new V1ObjectReference().apiVersion(object.getApiVersion())
            .kind(object.getKind())
            .namespace(object.getMetadata().getNamespace())
            .name(object.getMetadata().getName())
            .resourceVersion(object.getMetadata().getResourceVersion())
            .uid(object.getMetadata().getUid());
    }

    /**
     * Creates an event related to the object, adding reasonable defaults.
     * 
     *   * If `kind` is not set, it is set to "Event".
     *   * If `metadata.namespace` is not set, it is set 
     *     to the object's namespace.
     *   * If neither `metadata.name` nor `matadata.generateName` are set,
     *     set `generateName` to the object's name with a dash appended.
     *   * If `reportingInstance` is not set, set it to the object's name.
     *   * If `eventTime` is not set, set it to now.
     *   * If `type` is not set, set it to "Normal"
     *   * If `regarding` is not set, set it to the given object.
     *
     * @param client the client
     * @param object the object
     * @param event the event
     * @throws ApiException the api exception
     */
    @SuppressWarnings("PMD.NPathComplexity")
    public static void createEvent(ApiClient client,
            KubernetesObject object, EventsV1Event event)
            throws ApiException {
        if (Strings.isNullOrEmpty(event.getKind())) {
            event.kind("Event");
        }
        if (event.getMetadata() == null) {
            event.metadata(new V1ObjectMeta());
        }
        if (Strings.isNullOrEmpty(event.getMetadata().getNamespace())) {
            event.getMetadata().namespace(object.getMetadata().getNamespace());
        }
        if (Strings.isNullOrEmpty(event.getMetadata().getName())
            && Strings.isNullOrEmpty(event.getMetadata().getGenerateName())) {
            event.getMetadata()
                .generateName(object.getMetadata().getName() + "-");
        }
        if (Strings.isNullOrEmpty(event.getReportingInstance())) {
            event.reportingInstance(object.getMetadata().getName());
        }
        if (event.getEventTime() == null) {
            event.eventTime(OffsetDateTime.now());
        }
        if (Strings.isNullOrEmpty(event.getType())) {
            event.type("Normal");
        }
        if (event.getRegarding() == null) {
            event.regarding(objectReference(object));
        }
        new EventsV1Api(client).createNamespacedEvent(
            object.getMetadata().getNamespace(), event, null, null, null, null);
    }
}
