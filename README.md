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
mvn exec:java -Dexec.mainClass=SampleControllerMain

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

