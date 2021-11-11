# Sample Controller in Java Using Fabric8 Kubernetes Client

![Build](https://github.com/rohanKanojia/sample-controller-java/workflows/Java%20CI%20with%20Maven/badge.svg?branch=master)
![License](https://img.shields.io/github/license/rohanKanojia/sample-controller-java)
[![Twitter](https://img.shields.io/twitter/follow/fabric8io?style=social)](https://twitter.com/fabric8io)

## Note
> This repository implements a simple controller for watching Foo resources as defined with a CustomResourceDefinition (CRD).

This is a simple cont which implements a simple operator for a custom resource called `Foo`. Here 
is what this resource looks like:
```yaml
apiVersion: samplecontroller.k8s.io/v1alpha1
kind: Foo
metadata:
  name: example-foo
spec:
  deploymentName: example-foo-deploy
  replicas: 1
```

Each Foo object would have a child `Deployment` which would have a specified number of replicas in `Foo` object, so this controller just tries to maintain `Deployment` for a `Foo` resource checking whether a `Deployment` which is owned by `Foo` resource is running in cluster or not.

## How to Build
```
   mvn clean install
```

## How to Run
**Prerequisite**: Since the sample-controller uses apps/v1 deployments, the Kubernetes cluster version should be greater than 1.9.
```bash
# create a CustomResourceDefinition
kubectl create -f src/main/resources/crd.yaml

# Run Controller
mvn exec:java -Dexec.mainClass=io.fabric8.samplecontroller.SampleControllerMain

# create a custom resource of type Foo
kubectl create -f src/main/resources/example-foo.yaml

# check deployments created through the custom resource
kubectl get deployments
```

Make Sure that Foo Custom Resource Definition is already applied onto the cluster. If not, just apply it using this command:
```
kubectl apply -f src/main/resources/crd.yaml
```

Once everything is set, you can see that controller creating `Deployment` in your cluster for `Foo` resource:
```
[main] INFO SampleController - trying to fetch item from workqueue...
[main] INFO SampleController - Got default/example-foo
[pool-3-thread-1] INFO SampleController - handleDeploymentObject(example-foo-deploy)
[main] INFO SampleController - trying to fetch item from workqueue...
[main] INFO SampleController - Got default/example-foo
[pool-3-thread-1] INFO SampleController - enqueueFoo(example-foo)
[pool-3-thread-1] INFO SampleController - Going to enqueue key default/example-foo
[pool-3-thread-1] INFO SampleController - Adding item to workqueue
[main] INFO SampleController - trying to fetch item from workqueue...
[main] INFO SampleController - Got default/example-foo
[main] INFO SampleController - trying to fetch item from workqueue...
[main] INFO SampleController - Work Queue is empty
```

## Deploy to Kubernetes using [Kubernetes Maven Plugin](https://www.eclipse.org/jkube/docs/kubernetes-maven-plugin)

You can use Kubernetes Maven Plugin to build and push image to a registry like this:
- Build and Push image to some registry. Before that you'll need to configure `image.user` and `image.registry` properties in `pom.xml`:
```
mvn k8s:build k8s:push -Djkube.build.strategy=jib
```
![Building and Pushing Image](https://i.imgur.com/uJesL9q.png)

- Generate and Apply Kubernetes manifests to Kubernetes Cluster. First you'll need to configure ServiceAccount with correct roles.  Otherwise, you might get 403 from Kubernetes API server. Since our application would be using `default` ServiceAccount, we can configure it like this:

```
kubectl create clusterrolebinding default-pod --clusterrole cluster-admin --serviceaccount=default:default

# In case of some other namespace:
kubectl create clusterrolebinding default-pod --clusterrole cluster-admin --serviceaccount=<namespace>:default
```
Then you can go ahead and run resource and apply goals:
```
mvn k8s:resource k8s:apply
```
![Apply to Kubernetes](https://i.imgur.com/18hhsYp.png)

Check pods after deployment:
```
sample-controller-java : $ kubectl get pods
NAME                                      READY   STATUS    RESTARTS   AGE
sample-controller-java-654d478c4f-qk84r   1/1     Running   0          2m38s
```
Create some Foo resource and check if Deployment gets created:
```
sample-controller-java : $ kubectl create -f src/main/resources/cr.yaml 
foo.samplecontroller.k8s.io/example-foo created
sample-controller-java : $ kubectl get pods
NAME                                      READY   STATUS              RESTARTS   AGE
example-foo-deploy-9bbb75dc8-6tkf8        0/1     ContainerCreating   0          2s
sample-controller-java-654d478c4f-qk84r   1/1     Running             0          3m36s
```

