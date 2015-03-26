package de.bitdroid.jaxrs2retrofit;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.thoughtworks.qdox.builder.impl.EvaluatingVisitor;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaMethod;
import com.thoughtworks.qdox.model.JavaParameter;
import com.thoughtworks.qdox.model.JavaParameterizedType;
import com.thoughtworks.qdox.model.JavaType;
import com.thoughtworks.qdox.model.expression.Add;
import com.thoughtworks.qdox.model.expression.AnnotationValue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.Modifier;
import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

import de.bitdroid.jaxrs2retrofit.converter.AnnotatedParam;
import de.bitdroid.jaxrs2retrofit.converter.ParamConverter;
import de.bitdroid.jaxrs2retrofit.converter.ParamConverterManager;
import retrofit.client.Response;
import retrofit.http.Headers;

public final class RetrofitGenerator {

	private final RetrofitReturnStrategy retrofitReturnStrategy;
	private final String packageName;
	private final String excludedClassNamesRegex;
	private final Date currentDate;
	private final SimpleDateFormat dateFormat;
	private final ParamConverterManager paramConverterManager;


	public RetrofitGenerator(
			RetrofitReturnStrategy retrofitReturnStrategy,
			String packageName,
			String excludedClassNamesRegex,
			ParamConverterManager paramConverterManager) {

		this.retrofitReturnStrategy = retrofitReturnStrategy;
		this.packageName = packageName;
		this.excludedClassNamesRegex = excludedClassNamesRegex;
		this.currentDate = new Date();
		this.dateFormat = new SimpleDateFormat("dd.MM.yyyy 'at' HH:mm");
		this.paramConverterManager = paramConverterManager;
	}


	public JavaFile createResource(JavaClass jaxRsClass) {
		// find path annotation
		JavaAnnotation jaxRsPath = null;
		JavaAnnotation jaxRsConsumes = null;
		for (JavaAnnotation annotation : jaxRsClass.getAnnotations()) {
			String annotationType = annotation.getType().getFullyQualifiedName();
			if (annotationType.equals(Path.class.getName())) {
				jaxRsPath = annotation;
			} else if (annotationType.equals(Consumes.class.getName())) {
				jaxRsConsumes = annotation;
			}
		}
		if (jaxRsPath == null) return null; // no a valid JAX RS resource
		if (jaxRsClass.getName().matches(excludedClassNamesRegex)) return null;

		System.out.println(jaxRsClass.getName());
		TypeSpec.Builder retrofitResourceBuilder = TypeSpec
				.interfaceBuilder(jaxRsClass.getName())
				.addModifiers(Modifier.PUBLIC);
		addAboutJavadoc(retrofitResourceBuilder);

		for (JavaMethod jaxRsMethod : jaxRsClass.getMethods()) {
			List<MethodSpec> retrofitMethods = createMethod(jaxRsClass, jaxRsMethod, jaxRsPath, jaxRsConsumes);
			if (retrofitMethods != null) {
				for (MethodSpec method : retrofitMethods) {
					retrofitResourceBuilder.addMethod(method);
				}
			}
		}

		return JavaFile.builder(packageName, retrofitResourceBuilder.build()).build();
	}


	private void addAboutJavadoc(TypeSpec.Builder retrofitResourceBuilder) {
		StringBuilder aboutBuilder = new StringBuilder();
		aboutBuilder
				.append("This file was generated by ")
				.append("<a href=\"https://github.com/Maddoc42/JaxRs2Retrofit\">JaxRs2Retrofit</a> on ")
				.append(dateFormat.format(currentDate))
				.append(".\n");
		retrofitResourceBuilder.addJavadoc(aboutBuilder.toString());
	}


	private List<MethodSpec> createMethod(
			JavaClass jaxRsClass,
			JavaMethod jaxRsMethod,
			JavaAnnotation jaxRsPath,
			JavaAnnotation jaxRsConsumes) {

		RetrofitMethodBuilder retrofitMethodBuilder = new RetrofitMethodBuilder(
				jaxRsMethod.getName(),
				retrofitReturnStrategy);

		// find method type and path
		JavaAnnotation jaxRsMethodPath = null;
		HttpMethod httpMethod = null;
		for (JavaAnnotation annotation : jaxRsMethod.getAnnotations()) {
			String annotationType = annotation.getType().getFullyQualifiedName();
			if (annotationType.equals(Path.class.getName())) {
				jaxRsMethodPath = annotation;
			} else if (annotationType.equals(Consumes.class.getName())) {
				jaxRsConsumes = annotation;
			} else if (httpMethod == null) {
				httpMethod = HttpMethod.forJaxRsClassName(annotation.getType().getFullyQualifiedName());
			}
		}
		if (httpMethod == null) return null; // not a valid resource method
		EvaluatingVisitor evaluatingVisitor = new SimpleEvaluatingVisitor(jaxRsClass);

		// add path
		retrofitMethodBuilder.addAnnotation(createPathAnnotation(evaluatingVisitor, httpMethod, jaxRsPath, jaxRsMethodPath));

		// add content type
		if (jaxRsConsumes != null) {
			retrofitMethodBuilder.addAnnotation(createContentTypeAnnotation(evaluatingVisitor, jaxRsConsumes));
		}

		// create parameters
		for (JavaParameter jaxRsParameter : jaxRsMethod.getParameters()) {
			ParameterSpec spec = createParameter(jaxRsParameter);
			if (spec != null) retrofitMethodBuilder.addParameter(spec);
		}

		// create return type
		TypeName retrofitReturnType = createType(jaxRsMethod.getReturnType());
		if (retrofitReturnType.equals(TypeName.VOID)) {
			retrofitReturnType = ClassName.get(Response.class);
		}
		retrofitMethodBuilder.setReturnType(retrofitReturnType);

		return retrofitMethodBuilder.build();
	}


	private ParameterSpec createParameter(
			JavaParameter jaxRsParameter) {

		// find first annotation which can be converted, others are ignored
		JavaAnnotation jaxRsAnnotation = null;
		ClassName annotationType = null;
		TypeName paramType = createType(jaxRsParameter.getJavaClass());

		for (JavaAnnotation annotation : jaxRsParameter.getAnnotations()) {
			annotationType = (ClassName) createType(annotation.getType());
			jaxRsAnnotation = annotation;
			if (paramConverterManager.hasConverter(annotationType)) break;
		}

		// find suitable converter
		ParamConverter converter = paramConverterManager.getConverter(annotationType);

		// if no converter was found, ignore annotation
		if (converter == null) {
			annotationType = ClassName.get(Void.class);
			converter = paramConverterManager.getConverter(annotationType);
		}

		// if still no converted is found, ignore parameter completely (should (TM) only happen when
		// void converter has been explicitly removed
		if (converter == null) return null;

		// convert annotation and param
		AnnotatedParam param = new AnnotatedParam(
				paramType,
				annotationType,
				(jaxRsAnnotation == null) ? new HashMap<String, Object>() : jaxRsAnnotation.getNamedParameterMap());

		AnnotatedParam convertedParam = converter.convert(param);
		if (convertedParam == null) return null;

		// create code
		ParameterSpec.Builder retrofitParamBuilder = ParameterSpec
				.builder(convertedParam.getParamType(), jaxRsParameter.getName());

		AnnotationSpec.Builder retrofitParamAnnotationBuilder = AnnotationSpec
				.builder(convertedParam.getAnnotationType());

		if (jaxRsAnnotation != null) {
			for (Map.Entry<String, Object> entry : convertedParam.getAnnotationParameterMap().entrySet()) {
				retrofitParamAnnotationBuilder.addMember(entry.getKey(), entry.getValue().toString());
			}
		}
		retrofitParamBuilder.addAnnotation(retrofitParamAnnotationBuilder.build());
		return retrofitParamBuilder.build();
	}


	private final Pattern pathRegexPattern = Pattern.compile("\\{?(\\w+)(:[^\\{\\}]*)?\\}?");


	private AnnotationSpec createPathAnnotation(
			EvaluatingVisitor evaluatingVisitor,
			HttpMethod method,
			JavaAnnotation classPath,
			JavaAnnotation methodPath) {

		AnnotationValue pathExpression = classPath.getProperty("value");
		if (methodPath != null) {
			pathExpression = new Add(pathExpression, methodPath.getProperty("value"));
		}
		String value =  pathExpression.accept(evaluatingVisitor).toString();
		Matcher matcher = pathRegexPattern.matcher(value);
		StringBuilder regexFreeValue = new StringBuilder();
		while (matcher.find()) {
			regexFreeValue.append("/");
			String regexValue = matcher.group(0);
			if (regexValue.startsWith("{")) regexFreeValue
					.append("{")
					.append(matcher.group(1))
					.append("}");
			else regexFreeValue.append(matcher.group(1));
		}

		return AnnotationSpec.builder(method.getRetrofitClass())
				.addMember("value", "\"" + regexFreeValue.toString() + "\"")
				.build();
	}


	private AnnotationSpec createContentTypeAnnotation(
			EvaluatingVisitor evaluatingVisitor,
			JavaAnnotation consumesAnnotation) {

		AnnotationValue annotationValue = consumesAnnotation.getProperty("value");
		String stringAnnotationValue = annotationValue.getParameterValue().toString();

		String value = null;
		if (stringAnnotationValue.startsWith(MediaType.class.getSimpleName() + ".")) {
			String[] token = stringAnnotationValue.split("\\.");
			try {
				value = (String) MediaType.class.getDeclaredField(token[1]).get(null);
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
		} else {
			value = consumesAnnotation.getProperty("value").accept(evaluatingVisitor).toString();
		}

		return AnnotationSpec.builder(Headers.class)
				.addMember("value", "\"Content-type: " + value + "\"")
				.build();
	}


	private TypeName createType(JavaType jaxRsType) {
		if (void.class.getName().equals(jaxRsType.getFullyQualifiedName())) {
			return TypeName.VOID;

		} else if (boolean.class.getName().equals(jaxRsType.getFullyQualifiedName())) {
			return TypeName.BOOLEAN;

		} else if (int.class.getName().equals(jaxRsType.getFullyQualifiedName())) {
			return TypeName.INT;

		} else if (float.class.getName().equals(jaxRsType.getFullyQualifiedName())) {
			return TypeName.FLOAT;

		} else if (double.class.getName().equals(jaxRsType.getFullyQualifiedName())) {
			return TypeName.DOUBLE;

		} else if (short.class.getName().equals(jaxRsType.getFullyQualifiedName())) {
			return TypeName.SHORT;

		} else if (long.class.getName().equals(jaxRsType.getFullyQualifiedName())) {
			return TypeName.LONG;

		} else if (char.class.getName().equals(jaxRsType.getFullyQualifiedName())) {
			return TypeName.CHAR;

		} else if (byte.class.getName().equals(jaxRsType.getFullyQualifiedName())) {
			return TypeName.BYTE;

		// map jaxrs response objects to retrofit ones
		} else if (javax.ws.rs.core.Response.class.getName().equals(jaxRsType.getFullyQualifiedName())) {
			return ClassName.get(Response.class);

		} else if (jaxRsType instanceof JavaParameterizedType) {
			JavaParameterizedType parametrizedType = (JavaParameterizedType) jaxRsType;
			if (parametrizedType.getActualTypeArguments().size() == 0) {
				return ClassName.bestGuess(jaxRsType.getFullyQualifiedName());
			}

			ClassName outerType = ClassName.bestGuess(parametrizedType.getFullyQualifiedName());
			TypeName[] paramTypes = new TypeName[parametrizedType.getActualTypeArguments().size()];
			for (int i = 0; i < paramTypes.length; ++i) {
				paramTypes[i] = ClassName.bestGuess(parametrizedType.getActualTypeArguments().get(i).getFullyQualifiedName());
			}
			return ParameterizedTypeName.get(outerType, paramTypes);

		} else {
			return ClassName.bestGuess(jaxRsType.getFullyQualifiedName());
		}
	}

}
