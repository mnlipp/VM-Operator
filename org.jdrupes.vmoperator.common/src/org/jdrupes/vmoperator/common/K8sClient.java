/*
 * VM-Operator
 * Copyright (C) 2024 Michael N. Lipp
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

import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.ApiResponse;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.Pair;
import io.kubernetes.client.openapi.auth.Authentication;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.text.DateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.net.ssl.KeyManager;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * A client with some additional properties.
 */
@SuppressWarnings({ "PMD.ExcessivePublicCount", "PMD.TooManyMethods",
    "PMD.LinguisticNaming", "checkstyle:LineLength",
    "PMD.CouplingBetweenObjects", "PMD.GodClass" })
public class K8sClient extends ApiClient {

    private ApiClient apiClient;
    private PatchOptions defaultPatchOptions;

    /**
     * Instantiates a new client.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public K8sClient() throws IOException {
        defaultPatchOptions = new PatchOptions();
        defaultPatchOptions.setFieldManager("kubernetes-java-kubectl-apply");
    }

    private ApiClient apiClient() {
        if (apiClient == null) {
            try {
                apiClient = ClientBuilder.standard().build();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return apiClient;
    }

    /**
     * Gets the default patch options.
     *
     * @return the defaultPatchOptions
     */
    public PatchOptions defaultPatchOptions() {
        return defaultPatchOptions;
    }

    /**
     * Changes the default patch options.
     *
     * @param patchOptions the patch options
     * @return the client
     */
    public K8sClient with(PatchOptions patchOptions) {
        defaultPatchOptions = patchOptions;
        return this;
    }

    /**
     * Gets the base path.
     *
     * @return the base path
     * @see ApiClient#getBasePath()
     */
    @Override
    public String getBasePath() {
        return apiClient().getBasePath();
    }

    /**
     * Sets the base path.
     *
     * @param basePath the base path
     * @return the api client
     * @see ApiClient#setBasePath(java.lang.String)
     */
    @Override
    public ApiClient setBasePath(String basePath) {
        return apiClient().setBasePath(basePath);
    }

    /**
     * Gets the http client.
     *
     * @return the http client
     * @see ApiClient#getHttpClient()
     */
    @Override
    public OkHttpClient getHttpClient() {
        return apiClient().getHttpClient();
    }

    /**
     * Sets the http client.
     *
     * @param newHttpClient the new http client
     * @return the api client
     * @see ApiClient#setHttpClient(okhttp3.OkHttpClient)
     */
    @Override
    public ApiClient setHttpClient(OkHttpClient newHttpClient) {
        return apiClient().setHttpClient(newHttpClient);
    }

    /**
     * Gets the json.
     *
     * @return the json
     * @see ApiClient#getJSON()
     */
    @SuppressWarnings("abbreviationAsWordInName")
    @Override
    public JSON getJSON() {
        return apiClient().getJSON();
    }

    /**
     * Sets the JSON.
     *
     * @param json the json
     * @return the api client
     * @see ApiClient#setJSON(io.kubernetes.client.openapi.JSON)
     */
    @SuppressWarnings("abbreviationAsWordInName")
    @Override
    public ApiClient setJSON(JSON json) {
        return apiClient().setJSON(json);
    }

    /**
     * Checks if is verifying ssl.
     *
     * @return true, if is verifying ssl
     * @see ApiClient#isVerifyingSsl()
     */
    @Override
    public boolean isVerifyingSsl() {
        return apiClient().isVerifyingSsl();
    }

    /**
     * Sets the verifying ssl.
     *
     * @param verifyingSsl the verifying ssl
     * @return the api client
     * @see ApiClient#setVerifyingSsl(boolean)
     */
    @Override
    public ApiClient setVerifyingSsl(boolean verifyingSsl) {
        return apiClient().setVerifyingSsl(verifyingSsl);
    }

    /**
     * Gets the ssl ca cert.
     *
     * @return the ssl ca cert
     * @see ApiClient#getSslCaCert()
     */
    @Override
    public InputStream getSslCaCert() {
        return apiClient().getSslCaCert();
    }

    /**
     * Sets the ssl ca cert.
     *
     * @param sslCaCert the ssl ca cert
     * @return the api client
     * @see ApiClient#setSslCaCert(java.io.InputStream)
     */
    @Override
    public ApiClient setSslCaCert(InputStream sslCaCert) {
        return apiClient().setSslCaCert(sslCaCert);
    }

    /**
     * Gets the key managers.
     *
     * @return the key managers
     * @see ApiClient#getKeyManagers()
     */
    @Override
    public KeyManager[] getKeyManagers() {
        return apiClient().getKeyManagers();
    }

    /**
     * Sets the key managers.
     *
     * @param managers the managers
     * @return the api client
     * @see ApiClient#setKeyManagers(javax.net.ssl.KeyManager[])
     */
    @SuppressWarnings("PMD.UseVarargs")
    @Override
    public ApiClient setKeyManagers(KeyManager[] managers) {
        return apiClient().setKeyManagers(managers);
    }

    /**
     * Gets the date format.
     *
     * @return the date format
     * @see ApiClient#getDateFormat()
     */
    @Override
    public DateFormat getDateFormat() {
        return apiClient().getDateFormat();
    }

    /**
     * Sets the date format.
     *
     * @param dateFormat the date format
     * @return the api client
     * @see ApiClient#setDateFormat(java.text.DateFormat)
     */
    @Override
    public ApiClient setDateFormat(DateFormat dateFormat) {
        return apiClient().setDateFormat(dateFormat);
    }

    /**
     * Sets the sql date format.
     *
     * @param dateFormat the date format
     * @return the api client
     * @see ApiClient#setSqlDateFormat(java.text.DateFormat)
     */
    @Override
    public ApiClient setSqlDateFormat(DateFormat dateFormat) {
        return apiClient().setSqlDateFormat(dateFormat);
    }

    /**
     * Sets the offset date time format.
     *
     * @param dateFormat the date format
     * @return the api client
     * @see ApiClient#setOffsetDateTimeFormat(java.time.format.DateTimeFormatter)
     */
    @Override
    public ApiClient setOffsetDateTimeFormat(DateTimeFormatter dateFormat) {
        return apiClient().setOffsetDateTimeFormat(dateFormat);
    }

    /**
     * Sets the local date format.
     *
     * @param dateFormat the date format
     * @return the api client
     * @see ApiClient#setLocalDateFormat(java.time.format.DateTimeFormatter)
     */
    @Override
    public ApiClient setLocalDateFormat(DateTimeFormatter dateFormat) {
        return apiClient().setLocalDateFormat(dateFormat);
    }

    /**
     * Sets the lenient on json.
     *
     * @param lenientOnJson the lenient on json
     * @return the api client
     * @see ApiClient#setLenientOnJson(boolean)
     */
    @Override
    public ApiClient setLenientOnJson(boolean lenientOnJson) {
        return apiClient().setLenientOnJson(lenientOnJson);
    }

    /**
     * Gets the authentications.
     *
     * @return the authentications
     * @see ApiClient#getAuthentications()
     */
    @Override
    public Map<String, Authentication> getAuthentications() {
        return apiClient().getAuthentications();
    }

    /**
     * Gets the authentication.
     *
     * @param authName the auth name
     * @return the authentication
     * @see ApiClient#getAuthentication(java.lang.String)
     */
    @Override
    public Authentication getAuthentication(String authName) {
        return apiClient().getAuthentication(authName);
    }

    /**
     * Sets the username.
     *
     * @param username the new username
     * @see ApiClient#setUsername(java.lang.String)
     */
    @Override
    public void setUsername(String username) {
        apiClient().setUsername(username);
    }

    /**
     * Sets the password.
     *
     * @param password the new password
     * @see ApiClient#setPassword(java.lang.String)
     */
    @Override
    public void setPassword(String password) {
        apiClient().setPassword(password);
    }

    /**
     * Sets the api key.
     *
     * @param apiKey the new api key
     * @see ApiClient#setApiKey(java.lang.String)
     */
    @Override
    public void setApiKey(String apiKey) {
        apiClient().setApiKey(apiKey);
    }

    /**
     * Sets the api key prefix.
     *
     * @param apiKeyPrefix the new api key prefix
     * @see ApiClient#setApiKeyPrefix(java.lang.String)
     */
    @Override
    public void setApiKeyPrefix(String apiKeyPrefix) {
        apiClient().setApiKeyPrefix(apiKeyPrefix);
    }

    /**
     * Sets the access token.
     *
     * @param accessToken the new access token
     * @see ApiClient#setAccessToken(java.lang.String)
     */
    @Override
    public void setAccessToken(String accessToken) {
        apiClient().setAccessToken(accessToken);
    }

    /**
     * Sets the user agent.
     *
     * @param userAgent the user agent
     * @return the api client
     * @see ApiClient#setUserAgent(java.lang.String)
     */
    @Override
    public ApiClient setUserAgent(String userAgent) {
        return apiClient().setUserAgent(userAgent);
    }

    /**
     * To string.
     *
     * @return the string
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return apiClient().toString();
    }

    /**
     * Adds the default header.
     *
     * @param key the key
     * @param value the value
     * @return the api client
     * @see ApiClient#addDefaultHeader(java.lang.String, java.lang.String)
     */
    @Override
    public ApiClient addDefaultHeader(String key, String value) {
        return apiClient().addDefaultHeader(key, value);
    }

    /**
     * Adds the default cookie.
     *
     * @param key the key
     * @param value the value
     * @return the api client
     * @see ApiClient#addDefaultCookie(java.lang.String, java.lang.String)
     */
    @Override
    public ApiClient addDefaultCookie(String key, String value) {
        return apiClient().addDefaultCookie(key, value);
    }

    /**
     * Checks if is debugging.
     *
     * @return true, if is debugging
     * @see ApiClient#isDebugging()
     */
    @Override
    public boolean isDebugging() {
        return apiClient().isDebugging();
    }

    /**
     * Sets the debugging.
     *
     * @param debugging the debugging
     * @return the api client
     * @see ApiClient#setDebugging(boolean)
     */
    @Override
    public ApiClient setDebugging(boolean debugging) {
        return apiClient().setDebugging(debugging);
    }

    /**
     * Gets the temp folder path.
     *
     * @return the temp folder path
     * @see ApiClient#getTempFolderPath()
     */
    @Override
    public String getTempFolderPath() {
        return apiClient().getTempFolderPath();
    }

    /**
     * Sets the temp folder path.
     *
     * @param tempFolderPath the temp folder path
     * @return the api client
     * @see ApiClient#setTempFolderPath(java.lang.String)
     */
    @Override
    public ApiClient setTempFolderPath(String tempFolderPath) {
        return apiClient().setTempFolderPath(tempFolderPath);
    }

    /**
     * Gets the connect timeout.
     *
     * @return the connect timeout
     * @see ApiClient#getConnectTimeout()
     */
    @Override
    public int getConnectTimeout() {
        return apiClient().getConnectTimeout();
    }

    /**
     * Sets the connect timeout.
     *
     * @param connectionTimeout the connection timeout
     * @return the api client
     * @see ApiClient#setConnectTimeout(int)
     */
    @Override
    public ApiClient setConnectTimeout(int connectionTimeout) {
        return apiClient().setConnectTimeout(connectionTimeout);
    }

    /**
     * Gets the read timeout.
     *
     * @return the read timeout
     * @see ApiClient#getReadTimeout()
     */
    @Override
    public int getReadTimeout() {
        return apiClient().getReadTimeout();
    }

    /**
     * Sets the read timeout.
     *
     * @param readTimeout the read timeout
     * @return the api client
     * @see ApiClient#setReadTimeout(int)
     */
    @Override
    public ApiClient setReadTimeout(int readTimeout) {
        return apiClient().setReadTimeout(readTimeout);
    }

    /**
     * Gets the write timeout.
     *
     * @return the write timeout
     * @see ApiClient#getWriteTimeout()
     */
    @Override
    public int getWriteTimeout() {
        return apiClient().getWriteTimeout();
    }

    /**
     * Sets the write timeout.
     *
     * @param writeTimeout the write timeout
     * @return the api client
     * @see ApiClient#setWriteTimeout(int)
     */
    @Override
    public ApiClient setWriteTimeout(int writeTimeout) {
        return apiClient().setWriteTimeout(writeTimeout);
    }

    /**
     * Parameter to string.
     *
     * @param param the param
     * @return the string
     * @see ApiClient#parameterToString(java.lang.Object)
     */
    @Override
    public String parameterToString(Object param) {
        return apiClient().parameterToString(param);
    }

    /**
     * Parameter to pair.
     *
     * @param name the name
     * @param value the value
     * @return the list
     * @see ApiClient#parameterToPair(java.lang.String, java.lang.Object)
     */
    @Override
    public List<Pair> parameterToPair(String name, Object value) {
        return apiClient().parameterToPair(name, value);
    }

    /**
     * Parameter to pairs.
     *
     * @param collectionFormat the collection format
     * @param name the name
     * @param value the value
     * @return the list
     * @see ApiClient#parameterToPairs(java.lang.String, java.lang.String, java.util.Collection)
     */
    @SuppressWarnings({ "rawtypes", "PMD.AvoidDuplicateLiterals" })
    @Override
    public List<Pair> parameterToPairs(String collectionFormat, String name,
            Collection value) {
        return apiClient().parameterToPairs(collectionFormat, name, value);
    }

    /**
     * Collection path parameter to string.
     *
     * @param collectionFormat the collection format
     * @param value the value
     * @return the string
     * @see ApiClient#collectionPathParameterToString(java.lang.String, java.util.Collection)
     */
    @SuppressWarnings("rawtypes")
    @Override
    public String collectionPathParameterToString(String collectionFormat,
            Collection value) {
        return apiClient().collectionPathParameterToString(collectionFormat,
            value);
    }

    /**
     * Sanitize filename.
     *
     * @param filename the filename
     * @return the string
     * @see ApiClient#sanitizeFilename(java.lang.String)
     */
    @Override
    public String sanitizeFilename(String filename) {
        return apiClient().sanitizeFilename(filename);
    }

    /**
     * Checks if is json mime.
     *
     * @param mime the mime
     * @return true, if is json mime
     * @see ApiClient#isJsonMime(java.lang.String)
     */
    @Override
    public boolean isJsonMime(String mime) {
        return apiClient().isJsonMime(mime);
    }

    /**
     * Select header accept.
     *
     * @param accepts the accepts
     * @return the string
     * @see ApiClient#selectHeaderAccept(java.lang.String[])
     */
    @SuppressWarnings("PMD.UseVarargs")
    @Override
    public String selectHeaderAccept(String[] accepts) {
        return apiClient().selectHeaderAccept(accepts);
    }

    /**
     * Select header content type.
     *
     * @param contentTypes the content types
     * @return the string
     * @see ApiClient#selectHeaderContentType(java.lang.String[])
     */
    @SuppressWarnings("PMD.UseVarargs")
    @Override
    public String selectHeaderContentType(String[] contentTypes) {
        return apiClient().selectHeaderContentType(contentTypes);
    }

    /**
     * Escape string.
     *
     * @param str the str
     * @return the string
     * @see ApiClient#escapeString(java.lang.String)
     */
    @Override
    public String escapeString(String str) {
        return apiClient().escapeString(str);
    }

    /**
     * Deserialize.
     *
     * @param <T> the generic type
     * @param response the response
     * @param returnType the return type
     * @return the t
     * @throws ApiException the api exception
     * @see ApiClient#deserialize(okhttp3.Response, java.lang.reflect.Type)
     */
    @Override
    public <T> T deserialize(Response response, Type returnType)
            throws ApiException {
        return apiClient().deserialize(response, returnType);
    }

    /**
     * Serialize.
     *
     * @param obj the obj
     * @param contentType the content type
     * @return the request body
     * @throws ApiException the api exception
     * @see ApiClient#serialize(java.lang.Object, java.lang.String)
     */
    @Override
    public RequestBody serialize(Object obj, String contentType)
            throws ApiException {
        return apiClient().serialize(obj, contentType);
    }

    /**
     * Download file from response.
     *
     * @param response the response
     * @return the file
     * @throws ApiException the api exception
     * @see ApiClient#downloadFileFromResponse(okhttp3.Response)
     */
    @Override
    public File downloadFileFromResponse(Response response)
            throws ApiException {
        return apiClient().downloadFileFromResponse(response);
    }

    /**
     * Prepare download file.
     *
     * @param response the response
     * @return the file
     * @throws IOException Signals that an I/O exception has occurred.
     * @see ApiClient#prepareDownloadFile(okhttp3.Response)
     */
    @Override
    public File prepareDownloadFile(Response response) throws IOException {
        return apiClient().prepareDownloadFile(response);
    }

    /**
     * Execute.
     *
     * @param <T> the generic type
     * @param call the call
     * @return the api response
     * @throws ApiException the api exception
     * @see ApiClient#execute(okhttp3.Call)
     */
    @Override
    public <T> ApiResponse<T> execute(Call call) throws ApiException {
        return apiClient().execute(call);
    }

    /**
     * Execute.
     *
     * @param <T> the generic type
     * @param call the call
     * @param returnType the return type
     * @return the api response
     * @throws ApiException the api exception
     * @see ApiClient#execute(okhttp3.Call, java.lang.reflect.Type)
     */
    @Override
    public <T> ApiResponse<T> execute(Call call, Type returnType)
            throws ApiException {
        return apiClient().execute(call, returnType);
    }

    /**
     * Execute async.
     *
     * @param <T> the generic type
     * @param call the call
     * @param callback the callback
     * @see ApiClient#executeAsync(okhttp3.Call, io.kubernetes.client.openapi.ApiCallback)
     */
    @Override
    public <T> void executeAsync(Call call, ApiCallback<T> callback) {
        apiClient().executeAsync(call, callback);
    }

    /**
     * Execute async.
     *
     * @param <T> the generic type
     * @param call the call
     * @param returnType the return type
     * @param callback the callback
     * @see ApiClient#executeAsync(okhttp3.Call, java.lang.reflect.Type, io.kubernetes.client.openapi.ApiCallback)
     */
    @Override
    public <T> void executeAsync(Call call, Type returnType,
            ApiCallback<T> callback) {
        apiClient().executeAsync(call, returnType, callback);
    }

    /**
     * Handle response.
     *
     * @param <T> the generic type
     * @param response the response
     * @param returnType the return type
     * @return the t
     * @throws ApiException the api exception
     * @see ApiClient#handleResponse(okhttp3.Response, java.lang.reflect.Type)
     */
    @Override
    public <T> T handleResponse(Response response, Type returnType)
            throws ApiException {
        return apiClient().handleResponse(response, returnType);
    }

    /**
     * Builds the call.
     *
     * @param baseUrl the base url
     * @param path the path
     * @param method the method
     * @param queryParams the query params
     * @param collectionQueryParams the collection query params
     * @param body the body
     * @param headerParams the header params
     * @param cookieParams the cookie params
     * @param formParams the form params
     * @param authNames the auth names
     * @param callback the callback
     * @return the call
     * @throws ApiException the api exception
     * @see ApiClient#buildCall(java.lang.String, java.lang.String, java.util.List, java.util.List, java.lang.Object, java.util.Map, java.util.Map, java.util.Map, java.lang.String[], io.kubernetes.client.openapi.ApiCallback)
     */
    @SuppressWarnings({ "rawtypes", "PMD.ExcessiveParameterList" })
    @Override
    public Call buildCall(String baseUrl, String path, String method,
            List<Pair> queryParams, List<Pair> collectionQueryParams,
            Object body, Map<String, String> headerParams,
            Map<String, String> cookieParams, Map<String, Object> formParams,
            String[] authNames,
            ApiCallback callback) throws ApiException {
        return apiClient().buildCall(baseUrl, path, method, queryParams,
            collectionQueryParams, body, headerParams, cookieParams, formParams,
            authNames, callback);
    }

    /**
     * Builds the request.
     *
     * @param baseUrl the base url
     * @param path the path
     * @param method the method
     * @param queryParams the query params
     * @param collectionQueryParams the collection query params
     * @param body the body
     * @param headerParams the header params
     * @param cookieParams the cookie params
     * @param formParams the form params
     * @param authNames the auth names
     * @param callback the callback
     * @return the request
     * @throws ApiException the api exception
     * @see ApiClient#buildRequest(java.lang.String, java.lang.String, java.util.List, java.util.List, java.lang.Object, java.util.Map, java.util.Map, java.util.Map, java.lang.String[], io.kubernetes.client.openapi.ApiCallback)
     */
    @SuppressWarnings({ "rawtypes", "PMD.ExcessiveParameterList" })
    @Override
    public Request buildRequest(String baseUrl, String path, String method,
            List<Pair> queryParams, List<Pair> collectionQueryParams,
            Object body, Map<String, String> headerParams,
            Map<String, String> cookieParams, Map<String, Object> formParams,
            String[] authNames, ApiCallback callback) throws ApiException {
        return apiClient().buildRequest(baseUrl, path, method, queryParams,
            collectionQueryParams, body, headerParams, cookieParams, formParams,
            authNames, callback);
    }

    /**
     * Builds the url.
     *
     * @param baseUrl the base url
     * @param path the path
     * @param queryParams the query params
     * @param collectionQueryParams the collection query params
     * @return the string
     * @see ApiClient#buildUrl(java.lang.String, java.util.List, java.util.List)
     */
    @Override
    public String buildUrl(String baseUrl, String path, List<Pair> queryParams,
            List<Pair> collectionQueryParams) {
        return apiClient().buildUrl(baseUrl, path, queryParams,
            collectionQueryParams);
    }

    /**
     * Process header params.
     *
     * @param headerParams the header params
     * @param reqBuilder the req builder
     * @see ApiClient#processHeaderParams(java.util.Map, okhttp3.Request.Builder)
     */
    @Override
    public void processHeaderParams(Map<String, String> headerParams,
            Builder reqBuilder) {
        apiClient().processHeaderParams(headerParams, reqBuilder);
    }

    /**
     * Process cookie params.
     *
     * @param cookieParams the cookie params
     * @param reqBuilder the req builder
     * @see ApiClient#processCookieParams(java.util.Map, okhttp3.Request.Builder)
     */
    @Override
    public void processCookieParams(Map<String, String> cookieParams,
            Builder reqBuilder) {
        apiClient().processCookieParams(cookieParams, reqBuilder);
    }

    /**
     * Update params for auth.
     *
     * @param authNames the auth names
     * @param queryParams the query params
     * @param headerParams the header params
     * @param cookieParams the cookie params
     * @throws ApiException 
     * @see ApiClient#updateParamsForAuth(java.lang.String[], java.util.List, java.util.Map, java.util.Map)
     */
    @Override
    public void updateParamsForAuth(String[] authNames, List<Pair> queryParams,
            Map<String, String> headerParams, Map<String, String> cookieParams,
            String payload, String method, URI uri) throws ApiException {
        apiClient().updateParamsForAuth(authNames, queryParams, headerParams,
            cookieParams, payload, method, uri);
    }

    /**
     * Builds the request body form encoding.
     *
     * @param formParams the form params
     * @return the request body
     * @see ApiClient#buildRequestBodyFormEncoding(java.util.Map)
     */
    @Override
    public RequestBody
            buildRequestBodyFormEncoding(Map<String, Object> formParams) {
        return apiClient().buildRequestBodyFormEncoding(formParams);
    }

    /**
     * Builds the request body multipart.
     *
     * @param formParams the form params
     * @return the request body
     * @see ApiClient#buildRequestBodyMultipart(java.util.Map)
     */
    @Override
    public RequestBody
            buildRequestBodyMultipart(Map<String, Object> formParams) {
        return apiClient().buildRequestBodyMultipart(formParams);
    }

    /**
     * Guess content type from file.
     *
     * @param file the file
     * @return the string
     * @see ApiClient#guessContentTypeFromFile(java.io.File)
     */
    @Override
    public String guessContentTypeFromFile(File file) {
        return apiClient().guessContentTypeFromFile(file);
    }

}