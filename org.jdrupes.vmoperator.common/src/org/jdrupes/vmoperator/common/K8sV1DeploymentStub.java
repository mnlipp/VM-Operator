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

import io.kubernetes.client.Discovery.APIResource;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import java.util.List;
import java.util.Optional;

/**
 * A stub for pods (v1).
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class K8sV1DeploymentStub
        extends K8sGenericStub<V1Deployment, V1DeploymentList> {

    /** The deployment's context. */
    public static final APIResource CONTEXT = new APIResource("apps",
        List.of("v1"), "v1", "Pod", true, "deployments", "deployment");

    /**
     * Instantiates a new stub.
     *
     * @param client the client
     * @param namespace the namespace
     * @param name the name
     */
    protected K8sV1DeploymentStub(K8sClient client, String namespace,
            String name) {
        super(V1Deployment.class, V1DeploymentList.class, client,
            CONTEXT, namespace, name);
    }

    /**
     * Scales the deployment.
     *
     * @param replicas the replicas
     * @return the new model or empty if not successful
     * @throws ApiException the API exception
     */
    public Optional<V1Deployment> scale(int replicas) throws ApiException {
        return patch(V1Patch.PATCH_FORMAT_JSON_PATCH,
            new V1Patch("[{\"op\": \"replace\", \"path\": \"/spec/replicas"
                + "\", \"value\": " + replicas + "}]"),
            client.defaultPatchOptions());
    }

    /**
     * Gets the stub for the given namespace and name.
     *
     * @param client the client
     * @param namespace the namespace
     * @param name the name
     * @return the deployment stub
     */
    public static K8sV1DeploymentStub get(K8sClient client, String namespace,
            String name) {
        return new K8sV1DeploymentStub(client, namespace, name);
    }
}