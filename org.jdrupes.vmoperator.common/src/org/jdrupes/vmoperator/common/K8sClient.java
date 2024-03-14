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
    "PMD.LinguisticNaming", "checkstyle:LineLength" })
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
     * @return
     * @see ApiClient#getBasePath()
     */
    public String getBasePath() {
        return apiClient().getBasePath();
    }

    /**
     * @param basePath
     * @return
     * @see ApiClient#setBasePath(java.lang.String)
     */
    public ApiClient setBasePath(String basePath) {
        return apiClient().setBasePath(basePath);
    }

    /**
     * @return
     * @see ApiClient#getHttpClient()
     */
    public OkHttpClient getHttpClient() {
        return apiClient().getHttpClient();
    }

    /**
     * @param newHttpClient
     * @return
     * @see ApiClient#setHttpClient(okhttp3.OkHttpClient)
     */
    public ApiClient setHttpClient(OkHttpClient newHttpClient) {
        return apiClient().setHttpClient(newHttpClient);
    }

    /**
     * @return
     * @see ApiClient#getJSON()
     */
    @SuppressWarnings("abbreviationAsWordInName")
    public JSON getJSON() {
        return apiClient().getJSON();
    }

    /**
     * @param json
     * @return
     * @see ApiClient#setJSON(io.kubernetes.client.openapi.JSON)
     */
    @SuppressWarnings("abbreviationAsWordInName")
    public ApiClient setJSON(JSON json) {
        return apiClient().setJSON(json);
    }

    /**
     * @return
     * @see ApiClient#isVerifyingSsl()
     */
    public boolean isVerifyingSsl() {
        return apiClient().isVerifyingSsl();
    }

    /**
     * @param verifyingSsl
     * @return
     * @see ApiClient#setVerifyingSsl(boolean)
     */
    public ApiClient setVerifyingSsl(boolean verifyingSsl) {
        return apiClient().setVerifyingSsl(verifyingSsl);
    }

    /**
     * @return
     * @see ApiClient#getSslCaCert()
     */
    public InputStream getSslCaCert() {
        return apiClient().getSslCaCert();
    }

    /**
     * @param sslCaCert
     * @return
     * @see ApiClient#setSslCaCert(java.io.InputStream)
     */
    public ApiClient setSslCaCert(InputStream sslCaCert) {
        return apiClient().setSslCaCert(sslCaCert);
    }

    /**
     * @return
     * @see ApiClient#getKeyManagers()
     */
    public KeyManager[] getKeyManagers() {
        return apiClient().getKeyManagers();
    }

    /**
     * @param managers
     * @return
     * @see ApiClient#setKeyManagers(javax.net.ssl.KeyManager[])
     */
    @SuppressWarnings("PMD.UseVarargs")
    public ApiClient setKeyManagers(KeyManager[] managers) {
        return apiClient().setKeyManagers(managers);
    }

    /**
     * @return
     * @see ApiClient#getDateFormat()
     */
    public DateFormat getDateFormat() {
        return apiClient().getDateFormat();
    }

    /**
     * @param dateFormat
     * @return
     * @see ApiClient#setDateFormat(java.text.DateFormat)
     */
    public ApiClient setDateFormat(DateFormat dateFormat) {
        return apiClient().setDateFormat(dateFormat);
    }

    /**
     * @param dateFormat
     * @return
     * @see ApiClient#setSqlDateFormat(java.text.DateFormat)
     */
    public ApiClient setSqlDateFormat(DateFormat dateFormat) {
        return apiClient().setSqlDateFormat(dateFormat);
    }

    /**
     * @param dateFormat
     * @return
     * @see ApiClient#setOffsetDateTimeFormat(java.time.format.DateTimeFormatter)
     */
    public ApiClient setOffsetDateTimeFormat(DateTimeFormatter dateFormat) {
        return apiClient().setOffsetDateTimeFormat(dateFormat);
    }

    /**
     * @param dateFormat
     * @return
     * @see ApiClient#setLocalDateFormat(java.time.format.DateTimeFormatter)
     */
    public ApiClient setLocalDateFormat(DateTimeFormatter dateFormat) {
        return apiClient().setLocalDateFormat(dateFormat);
    }

    /**
     * @param lenientOnJson
     * @return
     * @see ApiClient#setLenientOnJson(boolean)
     */
    public ApiClient setLenientOnJson(boolean lenientOnJson) {
        return apiClient().setLenientOnJson(lenientOnJson);
    }

    /**
     * @return
     * @see ApiClient#getAuthentications()
     */
    public Map<String, Authentication> getAuthentications() {
        return apiClient().getAuthentications();
    }

    /**
     * @param authName
     * @return
     * @see ApiClient#getAuthentication(java.lang.String)
     */
    public Authentication getAuthentication(String authName) {
        return apiClient().getAuthentication(authName);
    }

    /**
     * @param username
     * @see ApiClient#setUsername(java.lang.String)
     */
    public void setUsername(String username) {
        apiClient().setUsername(username);
    }

    /**
     * @param password
     * @see ApiClient#setPassword(java.lang.String)
     */
    public void setPassword(String password) {
        apiClient().setPassword(password);
    }

    /**
     * @param apiKey
     * @see ApiClient#setApiKey(java.lang.String)
     */
    public void setApiKey(String apiKey) {
        apiClient().setApiKey(apiKey);
    }

    /**
     * @param apiKeyPrefix
     * @see ApiClient#setApiKeyPrefix(java.lang.String)
     */
    public void setApiKeyPrefix(String apiKeyPrefix) {
        apiClient().setApiKeyPrefix(apiKeyPrefix);
    }

    /**
     * @param accessToken
     * @see ApiClient#setAccessToken(java.lang.String)
     */
    public void setAccessToken(String accessToken) {
        apiClient().setAccessToken(accessToken);
    }

    /**
     * @param userAgent
     * @return
     * @see ApiClient#setUserAgent(java.lang.String)
     */
    public ApiClient setUserAgent(String userAgent) {
        return apiClient().setUserAgent(userAgent);
    }

    /**
     * @return
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return apiClient().toString();
    }

    /**
     * @param key
     * @param value
     * @return
     * @see ApiClient#addDefaultHeader(java.lang.String, java.lang.String)
     */
    public ApiClient addDefaultHeader(String key, String value) {
        return apiClient().addDefaultHeader(key, value);
    }

    /**
     * @param key
     * @param value
     * @return
     * @see ApiClient#addDefaultCookie(java.lang.String, java.lang.String)
     */
    public ApiClient addDefaultCookie(String key, String value) {
        return apiClient().addDefaultCookie(key, value);
    }

    /**
     * @return
     * @see ApiClient#isDebugging()
     */
    public boolean isDebugging() {
        return apiClient().isDebugging();
    }

    /**
     * @param debugging
     * @return
     * @see ApiClient#setDebugging(boolean)
     */
    public ApiClient setDebugging(boolean debugging) {
        return apiClient().setDebugging(debugging);
    }

    /**
     * @return
     * @see ApiClient#getTempFolderPath()
     */
    public String getTempFolderPath() {
        return apiClient().getTempFolderPath();
    }

    /**
     * @param tempFolderPath
     * @return
     * @see ApiClient#setTempFolderPath(java.lang.String)
     */
    public ApiClient setTempFolderPath(String tempFolderPath) {
        return apiClient().setTempFolderPath(tempFolderPath);
    }

    /**
     * @return
     * @see ApiClient#getConnectTimeout()
     */
    public int getConnectTimeout() {
        return apiClient().getConnectTimeout();
    }

    /**
     * @param connectionTimeout
     * @return
     * @see ApiClient#setConnectTimeout(int)
     */
    public ApiClient setConnectTimeout(int connectionTimeout) {
        return apiClient().setConnectTimeout(connectionTimeout);
    }

    /**
     * @return
     * @see ApiClient#getReadTimeout()
     */
    public int getReadTimeout() {
        return apiClient().getReadTimeout();
    }

    /**
     * @param readTimeout
     * @return
     * @see ApiClient#setReadTimeout(int)
     */
    public ApiClient setReadTimeout(int readTimeout) {
        return apiClient().setReadTimeout(readTimeout);
    }

    /**
     * @return
     * @see ApiClient#getWriteTimeout()
     */
    public int getWriteTimeout() {
        return apiClient().getWriteTimeout();
    }

    /**
     * @param writeTimeout
     * @return
     * @see ApiClient#setWriteTimeout(int)
     */
    public ApiClient setWriteTimeout(int writeTimeout) {
        return apiClient().setWriteTimeout(writeTimeout);
    }

    /**
     * @param param
     * @return
     * @see ApiClient#parameterToString(java.lang.Object)
     */
    public String parameterToString(Object param) {
        return apiClient().parameterToString(param);
    }

    /**
     * @param name
     * @param value
     * @return
     * @see ApiClient#parameterToPair(java.lang.String, java.lang.Object)
     */
    public List<Pair> parameterToPair(String name, Object value) {
        return apiClient().parameterToPair(name, value);
    }

    /**
     * @param collectionFormat
     * @param name
     * @param value
     * @return
     * @see ApiClient#parameterToPairs(java.lang.String, java.lang.String, java.util.Collection)
     */
    @SuppressWarnings({ "rawtypes", "PMD.AvoidDuplicateLiterals" })
    public List<Pair> parameterToPairs(String collectionFormat, String name,
            Collection value) {
        return apiClient().parameterToPairs(collectionFormat, name, value);
    }

    /**
     * @param collectionFormat
     * @param value
     * @return
     * @see ApiClient#collectionPathParameterToString(java.lang.String, java.util.Collection)
     */
    @SuppressWarnings("rawtypes")
    public String collectionPathParameterToString(String collectionFormat,
            Collection value) {
        return apiClient().collectionPathParameterToString(collectionFormat,
            value);
    }

    /**
     * @param filename
     * @return
     * @see ApiClient#sanitizeFilename(java.lang.String)
     */
    public String sanitizeFilename(String filename) {
        return apiClient().sanitizeFilename(filename);
    }

    /**
     * @param mime
     * @return
     * @see ApiClient#isJsonMime(java.lang.String)
     */
    public boolean isJsonMime(String mime) {
        return apiClient().isJsonMime(mime);
    }

    /**
     * @param accepts
     * @return
     * @see ApiClient#selectHeaderAccept(java.lang.String[])
     */
    @SuppressWarnings("PMD.UseVarargs")
    public String selectHeaderAccept(String[] accepts) {
        return apiClient().selectHeaderAccept(accepts);
    }

    /**
     * @param contentTypes
     * @return
     * @see ApiClient#selectHeaderContentType(java.lang.String[])
     */
    @SuppressWarnings("PMD.UseVarargs")
    public String selectHeaderContentType(String[] contentTypes) {
        return apiClient().selectHeaderContentType(contentTypes);
    }

    /**
     * @param str
     * @return
     * @see ApiClient#escapeString(java.lang.String)
     */
    public String escapeString(String str) {
        return apiClient().escapeString(str);
    }

    /**
     * @param <T>
     * @param response
     * @param returnType
     * @return
     * @throws ApiException
     * @see ApiClient#deserialize(okhttp3.Response, java.lang.reflect.Type)
     */
    public <T> T deserialize(Response response, Type returnType)
            throws ApiException {
        return apiClient().deserialize(response, returnType);
    }

    /**
     * @param obj
     * @param contentType
     * @return
     * @throws ApiException
     * @see ApiClient#serialize(java.lang.Object, java.lang.String)
     */
    public RequestBody serialize(Object obj, String contentType)
            throws ApiException {
        return apiClient().serialize(obj, contentType);
    }

    /**
     * @param response
     * @return
     * @throws ApiException
     * @see ApiClient#downloadFileFromResponse(okhttp3.Response)
     */
    public File downloadFileFromResponse(Response response)
            throws ApiException {
        return apiClient().downloadFileFromResponse(response);
    }

    /**
     * @param response
     * @return
     * @throws IOException
     * @see ApiClient#prepareDownloadFile(okhttp3.Response)
     */
    public File prepareDownloadFile(Response response) throws IOException {
        return apiClient().prepareDownloadFile(response);
    }

    /**
     * @param <T>
     * @param call
     * @return
     * @throws ApiException
     * @see ApiClient#execute(okhttp3.Call)
     */
    public <T> ApiResponse<T> execute(Call call) throws ApiException {
        return apiClient().execute(call);
    }

    /**
     * @param <T>
     * @param call
     * @param returnType
     * @return
     * @throws ApiException
     * @see ApiClient#execute(okhttp3.Call, java.lang.reflect.Type)
     */
    public <T> ApiResponse<T> execute(Call call, Type returnType)
            throws ApiException {
        return apiClient().execute(call, returnType);
    }

    /**
     * @param <T>
     * @param call
     * @param callback
     * @see ApiClient#executeAsync(okhttp3.Call, io.kubernetes.client.openapi.ApiCallback)
     */
    public <T> void executeAsync(Call call, ApiCallback<T> callback) {
        apiClient().executeAsync(call, callback);
    }

    /**
     * @param <T>
     * @param call
     * @param returnType
     * @param callback
     * @see ApiClient#executeAsync(okhttp3.Call, java.lang.reflect.Type, io.kubernetes.client.openapi.ApiCallback)
     */
    public <T> void executeAsync(Call call, Type returnType,
            ApiCallback<T> callback) {
        apiClient().executeAsync(call, returnType, callback);
    }

    /**
     * @param <T>
     * @param response
     * @param returnType
     * @return
     * @throws ApiException
     * @see ApiClient#handleResponse(okhttp3.Response, java.lang.reflect.Type)
     */
    public <T> T handleResponse(Response response, Type returnType)
            throws ApiException {
        return apiClient().handleResponse(response, returnType);
    }

    /**
     * @param path
     * @param method
     * @param queryParams
     * @param collectionQueryParams
     * @param body
     * @param headerParams
     * @param cookieParams
     * @param formParams
     * @param authNames
     * @param callback
     * @return
     * @throws ApiException
     * @see ApiClient#buildCall(java.lang.String, java.lang.String, java.util.List, java.util.List, java.lang.Object, java.util.Map, java.util.Map, java.util.Map, java.lang.String[], io.kubernetes.client.openapi.ApiCallback)
     */
    @SuppressWarnings({ "rawtypes", "PMD.ExcessiveParameterList" })
    public Call buildCall(String path, String method, List<Pair> queryParams,
            List<Pair> collectionQueryParams, Object body,
            Map<String, String> headerParams, Map<String, String> cookieParams,
            Map<String, Object> formParams, String[] authNames,
            ApiCallback callback) throws ApiException {
        return apiClient().buildCall(path, method, queryParams,
            collectionQueryParams, body, headerParams, cookieParams, formParams,
            authNames, callback);
    }

    /**
     * @param path
     * @param method
     * @param queryParams
     * @param collectionQueryParams
     * @param body
     * @param headerParams
     * @param cookieParams
     * @param formParams
     * @param authNames
     * @param callback
     * @return
     * @throws ApiException
     * @see ApiClient#buildRequest(java.lang.String, java.lang.String, java.util.List, java.util.List, java.lang.Object, java.util.Map, java.util.Map, java.util.Map, java.lang.String[], io.kubernetes.client.openapi.ApiCallback)
     */
    @SuppressWarnings({ "rawtypes", "PMD.ExcessiveParameterList" })
    public Request buildRequest(String path, String method,
            List<Pair> queryParams, List<Pair> collectionQueryParams,
            Object body, Map<String, String> headerParams,
            Map<String, String> cookieParams, Map<String, Object> formParams,
            String[] authNames, ApiCallback callback) throws ApiException {
        return apiClient().buildRequest(path, method, queryParams,
            collectionQueryParams, body, headerParams, cookieParams, formParams,
            authNames, callback);
    }

    /**
     * @param path
     * @param queryParams
     * @param collectionQueryParams
     * @return
     * @see ApiClient#buildUrl(java.lang.String, java.util.List, java.util.List)
     */
    public String buildUrl(String path, List<Pair> queryParams,
            List<Pair> collectionQueryParams) {
        return apiClient().buildUrl(path, queryParams, collectionQueryParams);
    }

    /**
     * @param headerParams
     * @param reqBuilder
     * @see ApiClient#processHeaderParams(java.util.Map, okhttp3.Request.Builder)
     */
    public void processHeaderParams(Map<String, String> headerParams,
            Builder reqBuilder) {
        apiClient().processHeaderParams(headerParams, reqBuilder);
    }

    /**
     * @param cookieParams
     * @param reqBuilder
     * @see ApiClient#processCookieParams(java.util.Map, okhttp3.Request.Builder)
     */
    public void processCookieParams(Map<String, String> cookieParams,
            Builder reqBuilder) {
        apiClient().processCookieParams(cookieParams, reqBuilder);
    }

    /**
     * @param authNames
     * @param queryParams
     * @param headerParams
     * @param cookieParams
     * @see ApiClient#updateParamsForAuth(java.lang.String[], java.util.List, java.util.Map, java.util.Map)
     */
    public void updateParamsForAuth(String[] authNames, List<Pair> queryParams,
            Map<String, String> headerParams,
            Map<String, String> cookieParams) {
        apiClient().updateParamsForAuth(authNames, queryParams, headerParams,
            cookieParams);
    }

    /**
     * @param formParams
     * @return
     * @see ApiClient#buildRequestBodyFormEncoding(java.util.Map)
     */
    public RequestBody
            buildRequestBodyFormEncoding(Map<String, Object> formParams) {
        return apiClient().buildRequestBodyFormEncoding(formParams);
    }

    /**
     * @param formParams
     * @return
     * @see ApiClient#buildRequestBodyMultipart(java.util.Map)
     */
    public RequestBody
            buildRequestBodyMultipart(Map<String, Object> formParams) {
        return apiClient().buildRequestBodyMultipart(formParams);
    }

    /**
     * @param file
     * @return
     * @see ApiClient#guessContentTypeFromFile(java.io.File)
     */
    public String guessContentTypeFromFile(File file) {
        return apiClient().guessContentTypeFromFile(file);
    }

}