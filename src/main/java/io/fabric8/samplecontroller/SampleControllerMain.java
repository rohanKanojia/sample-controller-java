package io.fabric8.samplecontroller;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.fabric8.samplecontroller.controller.SampleController;
import io.fabric8.samplecontroller.api.model.v1alpha1.Foo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Main Class for application, you can run this sample using this command:
 *
 *  mvn exec:java -Dexec.mainClass="io.fabric8.samplecontroller.SampleControllerMain"
 */
public class SampleControllerMain {
    public static final Logger logger = LoggerFactory.getLogger(SampleControllerMain.class.getSimpleName());

    public static void main(String[] args) {
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            String namespace = client.getNamespace();
            if (namespace == null) {
                logger.info("No namespace found via config, assuming default.");
                namespace = "default";
            }

            logger.info("Using namespace : {}", namespace);

            SharedInformerFactory informerFactory = client.informers();

            MixedOperation<Foo, KubernetesResourceList<Foo>, Resource<Foo>> fooClient = client.resources(Foo.class);
            SharedIndexInformer<Deployment> deploymentSharedIndexInformer = informerFactory.sharedIndexInformerFor(Deployment.class, 10 * 60 * 1000);
            SharedIndexInformer<Foo> fooSharedIndexInformer = informerFactory.sharedIndexInformerFor(Foo.class, 10 * 60 * 1000);
            SampleController sampleController = new SampleController(client, fooClient, deploymentSharedIndexInformer, fooSharedIndexInformer, namespace);

            informerFactory.addSharedInformerEventListener(exception -> logger.error("Exception occurred, but caught", exception));
            Future<Void> startInformersFuture = informerFactory.startAllRegisteredInformers();
            startInformersFuture.get();

            logger.info("Starting Foo Controller");
            sampleController.run();
        } catch (KubernetesClientException exception) {
            logger.error("Kubernetes Client Exception : ", exception);
        } catch (ExecutionException executionException) {
            logger.info("Exception in starting all registered informers ", executionException);
        } catch (InterruptedException interruptedException) {
            logger.info("Interrupted : ", interruptedException);
            Thread.currentThread().interrupt();
            interruptedException.printStackTrace();
        }
    }
}
