package io.fabric8.samplecontroller.api.model.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1alpha1")
@Group("samplecontroller.k8s.io")
@Plural("foos")
public class Foo extends CustomResource<FooSpec, FooStatus> implements Namespaced { }
