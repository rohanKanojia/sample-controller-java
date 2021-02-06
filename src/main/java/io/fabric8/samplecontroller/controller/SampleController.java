package io.fabric8.samplecontroller.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import io.fabric8.samplecontroller.api.model.v1alpha1.FooList;
import io.fabric8.samplecontroller.api.model.v1alpha1.Foo;
import io.fabric8.samplecontroller.api.model.v1alpha1.FooSpec;
import io.fabric8.samplecontroller.api.model.v1alpha1.FooStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class SampleController {
    private final BlockingQueue<String> workqueue;
    private final SharedIndexInformer<Foo> fooInformer;
    private final SharedIndexInformer<Deployment> deploymentInformer;
    private final Lister<Foo> fooLister;
    private final KubernetesClient kubernetesClient;
    private final MixedOperation<Foo, FooList, Resource<Foo>> fooClient;
    public static final Logger logger = LoggerFactory.getLogger(SampleController.class);

    public SampleController(KubernetesClient kubernetesClient, MixedOperation<Foo, FooList, Resource<Foo>> fooClient, SharedIndexInformer<Deployment> deploymentInformer, SharedIndexInformer<Foo> fooInformer, String namespace) {
        this.kubernetesClient = kubernetesClient;
        this.fooClient = fooClient;
        this.fooLister = new Lister<>(fooInformer.getIndexer(), namespace);
        this.fooInformer = fooInformer;
        this.deploymentInformer = deploymentInformer;
        this.workqueue = new ArrayBlockingQueue<>(1024);
    }

    public void create() {
        // Set up an event handler for when Foo resources change
        fooInformer.addEventHandler(new ResourceEventHandler<Foo>() {
            @Override
            public void onAdd(Foo foo) {
                enqueueFoo(foo);
            }

            @Override
            public void onUpdate(Foo foo, Foo newFoo) {
                enqueueFoo(newFoo);
            }

            @Override
            public void onDelete(Foo foo, boolean b) {
                // Do nothing
            }
        });

        // Set up an event handler for when Deployment resources change. This
        // handler will lookup the owner of the given Deployment, and if it is
        // owned by a Foo resource will enqueue that Foo resource for
        // processing. This way, we don't need to implement custom logic for
        // handling Deployment resources. More info on this pattern:
        // https://github.com/kubernetes/community/blob/8cafef897a22026d42f5e5bb3f104febe7e29830/contributors/devel/controllers.md
        deploymentInformer.addEventHandler(new ResourceEventHandler<Deployment>() {
            @Override
            public void onAdd(Deployment deployment) {
                handleObject(deployment);
            }

            @Override
            public void onUpdate(Deployment oldDeployment, Deployment newDeployment) {
                // Periodic resync will send update events for all known Deployments.
                // Two different versions of the same Deployment will always have different RVs.
                if (oldDeployment.getMetadata().getResourceVersion().equals(newDeployment.getMetadata().getResourceVersion())) {
                    return;
                }
                handleObject(newDeployment);
            }

            @Override
            public void onDelete(Deployment deployment, boolean b) {
                handleObject(deployment);
            }
        });
    }

    public void run() {
        logger.info("Starting {} controller", Foo.class.getSimpleName());
        logger.info("Waiting for informer caches to sync");
        while (!deploymentInformer.hasSynced() || !fooInformer.hasSynced()) {
            // Wait till Informer syncs
        }

        while (!Thread.currentThread().isInterrupted()) {
            try {
                logger.info("trying to fetch item from workqueue...");
                if (workqueue.isEmpty()) {
                    logger.info("Work Queue is empty");
                }
                String key = workqueue.take();
                Objects.requireNonNull(key, "key can't be null");
                logger.info("Got {}", key);
                if (key.isEmpty() || (!key.contains("/"))) {
                    logger.warn("invalid resource key: {}", key);
                }

                // Get the Foo resource's name from key which is in format namespace/name
                String name = key.split("/")[1];
                Foo foo = fooLister.get(key.split("/")[1]);
                if (foo == null) {
                    logger.error("Foo {} in workqueue no longer exists", name);
                    return;
                }
                reconcile(foo);

            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                logger.error("controller interrupted..");
            }
        }
    }

    /**
     * Compares the actual state with the desired, and attempts to
     * converge the two. It then updates the Status block of the Foo resource
     * with the current status of the resource.
     *
     * @param foo specified resource
     */
    protected void reconcile(Foo foo) {
        String deploymentName = foo.getSpec().getDeploymentName();
        if (deploymentName == null || deploymentName.isEmpty()) {
            // We choose to absorb the error here as the worker would requeue the
            // resource otherwise. Instead, the next time the resource is updated
            // the resource will be queued again.
            logger.warn("No Deployment name specified for Foo {}/{}", foo.getMetadata().getNamespace(), foo.getMetadata().getName());
            return;
        }

        // Get the deployment with the name specified in Foo.spec
        Deployment deployment = kubernetesClient.apps().deployments().inNamespace(foo.getMetadata().getNamespace()).withName(deploymentName).get();
        // If the resource doesn't exist, we'll create it
        if (deployment == null) {
            createDeployments(foo);
            return;
        }

        // If the Deployment is not controlled by this Foo resource, we should log
        // a warning to the event recorder and return error msg.
        if (!isControlledBy(deployment, foo)) {
            logger.warn("Deployment {} is not controlled by Foo {}", deployment.getMetadata().getName(), foo.getMetadata().getName());
            return;
        }

        // If this number of the replicas on the Foo resource is specified, and the
        // number does not equal the current desired replicas on the Deployment, we
        // should update the Deployment resource.
        if (foo.getSpec().getReplicas() != deployment.getSpec().getReplicas()) {
            logger.info("Foo {} replicas: {}, Deployment {} replicas: {}", foo.getMetadata().getName(), foo.getSpec().getReplicas(),
                    deployment.getMetadata().getName(), deployment.getSpec().getReplicas());
            deployment.getSpec().setReplicas(foo.getSpec().getReplicas());
            kubernetesClient.apps().deployments()
                    .inNamespace(foo.getMetadata().getNamespace())
                    .withName(deployment.getMetadata().getNamespace())
                    .replace(deployment);
        }

        // Finally, we update the status block of the Foo resource to reflect the
        // current state of the world
        updateAvailableReplicasInFooStatus(foo, foo.getSpec().getReplicas());
    }

    private void createDeployments(Foo foo) {
        Deployment deployment = createNewDeployment(foo);
        kubernetesClient.apps().deployments().inNamespace(foo.getMetadata().getNamespace()).create(deployment);
    }

    private void enqueueFoo(Foo foo) {
        logger.info("enqueueFoo({})", foo.getMetadata().getName());
        String key = Cache.metaNamespaceKeyFunc(foo);
        logger.info("Going to enqueue key {}", key);
        if (key != null && !key.isEmpty()) {
            logger.info("Adding item to workqueue");
            workqueue.add(key);
        }
    }

    private void handleObject(HasMetadata obj) {
        logger.info("handleDeploymentObject({})", obj.getMetadata().getName());
        OwnerReference ownerReference = getControllerOf(obj);
        Objects.requireNonNull(ownerReference);
        if (!ownerReference.getKind().equalsIgnoreCase(Foo.class.getSimpleName())) {
            return;
        }
        Foo foo = fooLister.get(ownerReference.getName());
        if (foo == null) {
            logger.info("ignoring orphaned object '{}' of foo '{}'", obj.getMetadata().getSelfLink(), ownerReference.getName());
            return;
        }
        enqueueFoo(foo);
    }

    private void updateAvailableReplicasInFooStatus(Foo foo, int replicas) {
        FooStatus fooStatus = new FooStatus();
        fooStatus.setAvailableReplicas(replicas);
        // NEVER modify objects from the store. It's a read-only, local cache.
        // You can create a copy manually and modify it
        Foo fooClone = getFooClone(foo);
        fooClone.setStatus(fooStatus);
        // If the CustomResourceSubresources feature gate is not enabled,
        // we must use Update instead of UpdateStatus to update the Status block of the Foo resource.
        // UpdateStatus will not allow changes to the Spec of the resource,
        // which is ideal for ensuring nothing other than resource status has been updated.
        fooClient.inNamespace(foo.getMetadata().getNamespace()).withName(foo.getMetadata().getName()).updateStatus(foo);
    }

    /**
     * createNewDeployment creates a new Deployment for a Foo resource. It also sets
     * the appropriate OwnerReferences on the resource so handleObject can discover
     * the Foo resource that 'owns' it.
     * @param foo {@link Foo} resource which will be owner of this Deployment
     * @return Deployment object based on this Foo resource
     */
    private Deployment createNewDeployment(Foo foo) {
        return new DeploymentBuilder()
                .withNewMetadata()
                  .withName(foo.getSpec().getDeploymentName())
                  .withNamespace(foo.getMetadata().getNamespace())
                  .withLabels(getDeploymentLabels(foo))
                  .addNewOwnerReference().withController(true).withKind(foo.getKind()).withApiVersion(foo.getApiVersion()).withName(foo.getMetadata().getName()).withNewUid(foo.getMetadata().getUid()).endOwnerReference()
                .endMetadata()
                .withNewSpec()
                  .withReplicas(foo.getSpec().getReplicas())
                  .withNewSelector()
                  .withMatchLabels(getDeploymentLabels(foo))
                  .endSelector()
                  .withNewTemplate()
                     .withNewMetadata().withLabels(getDeploymentLabels(foo)).endMetadata()
                     .withNewSpec()
                         .addNewContainer()
                         .withName("nginx")
                         .withImage("nginx:latest")
                         .endContainer()
                     .endSpec()
                  .endTemplate()
                .endSpec()
                .build();
    }

    private Map<String, String> getDeploymentLabels(Foo foo) {
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "nginx");
        labels.put("controller", foo.getMetadata().getName());
        return labels;
    }

    private OwnerReference getControllerOf(HasMetadata obj) {
        List<OwnerReference> ownerReferences = obj.getMetadata().getOwnerReferences();
        for (OwnerReference ownerReference : ownerReferences) {
            if (ownerReference.getController().equals(Boolean.TRUE)) {
                return ownerReference;
            }
        }
        return null;
    }

    private boolean isControlledBy(HasMetadata obj, Foo foo) {
        OwnerReference ownerReference = getControllerOf(obj);
        if (ownerReference != null) {
            return ownerReference.getKind().equals(foo.getKind()) && ownerReference.getName().equals(foo.getMetadata().getName());
        }
        return false;
    }

    private Foo getFooClone(Foo foo) {
        Foo cloneFoo = new Foo();
        FooSpec cloneFooSpec = new FooSpec();
        cloneFooSpec.setDeploymentName(foo.getSpec().getDeploymentName());
        cloneFooSpec.setReplicas(foo.getSpec().getReplicas());

        cloneFoo.setSpec(cloneFooSpec);
        cloneFoo.setMetadata(foo.getMetadata());

        return cloneFoo;
    }
}
