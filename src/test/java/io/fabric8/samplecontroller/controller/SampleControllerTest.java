package io.fabric8.samplecontroller.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.samplecontroller.api.model.v1alpha1.Foo;
import io.fabric8.samplecontroller.api.model.v1alpha1.FooSpec;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnableKubernetesMockClient
class SampleControllerTest {
    private KubernetesMockServer server;
    private KubernetesClient client;
    private static final long RESYNC_PERIOD_MILLIS = 10 * 60 * 1000L;

    @Test
    @DisplayName("Should create deployment for with respect to a specified Foo")
    void testReconcile() throws InterruptedException, JsonProcessingException {
        // Given
        String testNamespace = "ns1";
        Foo testFoo = getFoo("example-foo", testNamespace, "0800cff3-9d80-11ea-8973-0e13a02d8ebd", "example-foo-deploy");
        server.expect().post().withPath("/apis/apps/v1/namespaces/" + testNamespace + "/deployments")
                .andReturn(HttpURLConnection.HTTP_CREATED, new DeploymentBuilder().withNewMetadata().withName(testFoo.getSpec().getDeploymentName()).endMetadata().build())
                .times(testFoo.getSpec().getReplicas());

        SharedInformerFactory informerFactory = client.informers();
        MixedOperation<Foo, KubernetesResourceList<Foo>, Resource<Foo>> fooClient = client.resources(Foo.class);
        SharedIndexInformer<Deployment> deploymentSharedIndexInformer = informerFactory.sharedIndexInformerFor(Deployment.class, RESYNC_PERIOD_MILLIS);
        SharedIndexInformer<Foo> fooSharedIndexInformer = informerFactory.sharedIndexInformerFor(Foo.class, RESYNC_PERIOD_MILLIS);
        SampleController sampleController = new SampleController(client, fooClient, deploymentSharedIndexInformer, fooSharedIndexInformer, testNamespace);

        // When
        sampleController.reconcile(testFoo);

        // Then
        RecordedRequest recordedRequest = server.getLastRequest();
        assertEquals("POST", recordedRequest.getMethod());

        String requestBody = recordedRequest.getBody().readUtf8();
        assertNotNull(requestBody);
        Deployment deploymentInRequest = Serialization.jsonMapper().readValue(requestBody, Deployment.class);
        assertNotNull(deploymentInRequest);
        assertEquals(testFoo.getSpec().getDeploymentName(), deploymentInRequest.getMetadata().getName());
        assertEquals(1, deploymentInRequest.getMetadata().getOwnerReferences().size());
        assertEquals(testFoo.getMetadata().getName(), deploymentInRequest.getMetadata().getOwnerReferences().get(0).getName());
    }

    private Foo getFoo(String name, String testNamespace, String uid, String deploymentName) {
        Foo foo = new Foo();
        FooSpec fooSpec = new FooSpec();
        fooSpec.setReplicas(5);
        fooSpec.setDeploymentName(deploymentName);

        foo.setSpec(fooSpec);
        foo.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace(testNamespace).withUid(uid).build());
        return foo;
    }
}
