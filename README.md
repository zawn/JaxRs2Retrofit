
[![Build Status](https://travis-ci.org/Maddoc42/JaxRs2Retrofit.svg?branch=master)](https://travis-ci.org/Maddoc42/JaxRs2Retrofit)
[ ![Download](https://api.bintray.com/packages/maddoc42/maven/jaxrs2retrofit/images/download.svg) ](https://bintray.com/maddoc42/maven/jaxrs2retrofit/_latestVersion)
[![Coverage Status](https://coveralls.io/repos/Maddoc42/JaxRs2Retrofit/badge.svg?branch=master)](https://coveralls.io/r/Maddoc42/JaxRs2Retrofit?branch=master)

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
  void getHelloWorld(@Path("path") String path, Callback<String> callback);

  @GET("/helloworld/{path}")
  Observable<String> getHelloWorld(@Path("path") String path);
  
  @GET("/helloworld/{path}")
  String getHelloWorldSynchronously(@Path("path") String path);

}
```

## Download and install

JaxRs2Retrofit is available at [jcenter](https://bintray.com/maddoc42/maven/jaxrs2retrofit/). To start using the plugin apply the following to your 'build.gradle'

```groovy
apply plugin: 'de.bitdroid.jaxrs2retrofit'

buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'de.bitdroid.jaxrs2retrofit:plugin:0.4.1'
    }
}
```

which creates a new gradle task called `jaxRs2Retrofit` that will generate the client interfaces.

## Configuration for regular Java

While configuration is optional, below is a minimal set of options that you will probably want to change:

```groovy
jaxRs2Retrofit {
    inputDir = file('your/jax/rs/sources/files') // e.g. file(project(':example-server').projectDir.toString() + '/src/main/java'
    outputDir = file('where/Retrofit/files/should/go') // e.g. file('src/main/java-gen')
    packageName = 'your.package.name '
}
```

### Configuration for Android

In case you are developing for Android, configuring the plugin has to be done slightly different, as the plugin will create one JaxRs2Retrofit task for each build variant (e.g. debug, release, demo, ...).

```groovy
def generatedSourcesDir = file('src/main/java-gen')
def mainSourcesDir = file('src/main/java')

android {
    ...
    sourceSets {
        main {
            java {
                srcDir mainSourcesDir
                srcDir generatedSourcesDir
            }
        }
    }
}

afterEvaluate {
    project.tasks.matching { it.name.startsWith('jaxRs2Retrofit') }.each {
        it.inputDir = file('/path/to/my/jaxrs/sources')
        it.outputDir = generatedSourcesDir
        it.packageName = 'de.bitdroid.jaxrs2retrofit'
    }
}
```

### Generating only blocking / callback / Observable Retrofit methods

JaxRs2Retrofit supports generating the following Retrofit methods for each (!) JaxRs method:

- **blocking**: the "regular" Retrofit methods which will block the current thread when being called, e.g. ```public String getHelloWorld();```
- **callback**: method which has a [Retrofit `Callback`](https://square.github.io/retrofit/javadoc/retrofit/Callback.html) as an parameter, e.g. ```public void getHelloWorld(Callback<String> callback);```
- **observable**: returns an [JaxRs Observable Object](https://github.com/ReactiveX/RxJava/wiki/Observable), for those who love reactive programming, e.g. ```public Observable<String> getHelloWorld();```

Not every project is interested in using all three options (especially since JaxRs introduces a new dependency),
so which method 'type' gets generated can be configured like


```groovy
jaxRs2Retrofit {
    ...
    generateSynchronousMethods = true
    generateCallbackMethods = true
    generateRxJavaMethods = true
}
```

Default is `true` for all three.


### Ignoring certain resources

In case some JaxRs resources should not be processed (e.g. your super secret admin interface which nobody should know about), a Java regex for matchign resource names can be configured in the gradle task:

```groovy
jaxRs2Retrofit {
    ...
    excludedClassNamesRegex = "MySuperSecretAdminResource"
}
```

### Processing custom annotations

By default JaxRs2Retrofit will drop all parameter annotations that it does not know how to deal with, like `@Auth User user`. This behaviour can be customized registering a custom [`ParamConverter`](https://github.com/Maddoc42/JaxRs2Retrofit/blob/master/plugin/src/main/java/de/bitdroid/jaxrs2retrofit/converter/ParamConverter.java), for example in the build script:

```groovy
jaxRs2Retrofit {
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

Some prebuilt converters that might be handy:

- [`MapperConverter`](https://github.com/Maddoc42/JaxRs2Retrofit/blob/master/plugin/src/main/java/de/bitdroid/jaxrs2retrofit/converter/MappingConverter.java) for mapping one annotation to anther while keeping annotation arguments and parameter type
- [`IgnoreConverter`](https://github.com/Maddoc42/JaxRs2Retrofit/blob/master/plugin/src/main/java/de/bitdroid/jaxrs2retrofit/converter/IgnoreConverter.java) which ignores a parameter completely


## Example

For an example of how JaxRs2Retrofit can be configured on a real application have a look at the following `example-*` folders in the repository:

- **example-server**: a simple JaxRs server application (built using [Dropwizard](http://www.dropwizard.io/))
- **example-common**: model classes that are being shared between potential clients and the server app
- **example-java**: a (very) short Java application which has JaxRs2Retrofit configured to create Retrofit interfaces based on `example-server`
- **example-android**: same as `example-java` but using Android instead

Note that all four components are configured as Gradle modules (see the `settings.gradle`), which allows the server and clients to depend on the `example-common` module (see `compile project(':example-common')` in the respective `build.gradle` files).


## Features

- Support for `GET`, `PUT`, `POST`, `DELETE` and `HEAD` http methods
- Converts `QueryParam`, `PathParam` and `HeaderParam` to their Retrofit counterpart
- Return values can be configured to use `retrofit.Callback`, `rx.Observable`, behave normally or use all three
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
