
[![Build Status](https://travis-ci.org/Maddoc42/JaxRs2Retrofit.svg?branch=master)](https://travis-ci.org/Maddoc42/JaxRs2Retrofit)
[ ![Download](https://api.bintray.com/packages/maddoc42/maven/jaxrs2retrofit/images/download.svg) ](https://bintray.com/maddoc42/maven/jaxrs2retrofit/_latestVersion)

# JaxRs2Retrofit

Creates [Retrofit](https://github.com/square/retrofit) classes based on
[JAX-RS](https://de.wikipedia.org/wiki/Java_API_for_RESTful_Web_Services) interfaces. 

For example, given the following JAX-RS definition


```java
package serverPackage;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/helloworld")
public interface SimpleResource {

	@GET
	@Path("/{path}")
	public String getHelloWorld(@PathParam("path") String path);

}
```

JaxRs2Retrofit will generate the Retrofit interface below

```java
package clientPackage;

import retrofit.Callback;
import retrofit.http.GET;
import retrofit.http.Path;

public interface SimpleResource {

  @GET("/helloworld/{path}")
  String getHelloWorld(@Path("path") String path);
  
  @GET("/helloworld/{path}")
  void getHelloWorld(@Path("path") String path, Callback<String> callback);

}
```

## Download

JaxRs2Retrofit can either be used as a gradle plugin, by directly using the generator classes or as a 
standalone jar.

The first two are available at available at [jcenter](https://bintray.com/maddoc42/maven/jaxrs2retrofit/).
For gradle based builds:

`'de.bitdroid.jaxrs2retrofit:plugin:<latest_version>'`

## Using the gradle plugin

In order to create a new gradle task that will generate Retrofit interfaces you must first include JaxRs2Retrofit
in the buildscript section

```groovy
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'de.bitdroid.jaxrs2retrofit:plugin:<latest_version>'
    }
}
```

and create a new task

```groovy
task jaxrs2retrofit(type: de.bitdroid.jaxrs2retrofit.JaxRs2RetrofitTask) {
    inputDir = new File("</my/awesome/resources>")
    outputDir = new File(project.projectDir.toString() + "/target/generated-sources/jaxrs2retrofit/")
    retrofitPackageName = "<my/awesome/package"
}
project.compileJava.dependsOn jaxrs2retrofit
project.compileJava.source += jaxrs2retrofit.outputs.files
```

- `inputDir`: location of the JAX RS sources, e.g. `new File(project.projectDir.toString() + "/src/main/java")`
- `outputDir`: where the generated `.java` files should be stored
- `retrofitPackagename`: package name of generated files

### Ignoring certain resources

In case some JaxRs resources should not be processed (e.g. your super secret admin interface which nobody should know about), a Java regex for matchign resource names can be configured in the gradle task:

```groovy
task jaxrs2retrofit(type: de.bitdroid.jaxrs2retrofit.JaxRs2RetrofitTask) {
    ...
    excludedClassNamesRegex = "MySuperSecretAdminResource"
}
```

## Processing custom annotations

By default JaxRs2Retrofit will drop all parameter annotations that it does not know how to deal with, like `@Auth User user`. This behaviour can be customized registering a custom [`ParamConverter`](https://github.com/Maddoc42/JaxRs2Retrofit/blob/master/plugin/src/main/java/de/bitdroid/jaxrs2retrofit/converter/ParamConverter.java), for example in the build script:

```groovy
task jaxrs2retrofit(type: de.bitdroid.jaxrs2retrofit.JaxRs2RetrofitTask) {
    ...
    paramConverterManager.registerConverter(ClassName.get(QueryParam.class), new ParamConverter() {
        @Override
        AnnotatedParam convert(AnnotatedParam param) {
            return new AnnotatedParam(
                    ClassName.get(String.class),
                    param.getAnnotationType(),
                    param.getAnnotationParameterMap());
        }
    });
```

This will convert all parameters annotated with `@QueryParam("<value">)` to `@Query("<value">) String`.

To create your own converter you will need to supply a [`ParamConverter`](https://github.com/Maddoc42/JaxRs2Retrofit/blob/master/plugin/src/main/java/de/bitdroid/jaxrs2retrofit/converter/ParamConverter.java) which processes [`AnnotatedParam`](https://github.com/Maddoc42/JaxRs2Retrofit/blob/master/plugin/src/main/java/de/bitdroid/jaxrs2retrofit/converter/AnnotatedParam.java) instances and map it to a [`ClassName`](https://square.github.io/javapoet/javadoc/javapoet/com/squareup/javapoet/ClassName.html).


### Using the standalone jar

Running the jar without any arguments will print a short help message. Options are similar to those of the gradle task.


## Features

- Support for `GET`, `PUT`, `POST`, `DELETE` and `HEAD` http methods
- Converts `QueryParam`, `PathParam` and `HeaderParam` to their Retrofit counterpart
- Return values can be configured to use `retrofit.Callback`, behave normally or use both
- Skip classes / methods that lack JAX RS annotations
- Map `javax.ws.rs.core.Response` to `retrofit.client.Response`
- Custom annotation processing (e.g. `@Auth`) via [`ParamConverter`](https://github.com/Maddoc42/JaxRs2Retrofit/blob/master/plugin/src/main/java/de/bitdroid/jaxrs2retrofit/converter/ParamConverter.java)


## License
Copyright 2015 Philipp Eichhorn 

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
