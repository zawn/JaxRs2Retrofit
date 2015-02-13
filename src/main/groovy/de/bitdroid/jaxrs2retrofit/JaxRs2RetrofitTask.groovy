package de.bitdroid.jaxrs2retrofit
import com.squareup.javapoet.JavaFile
import com.thoughtworks.qdox.JavaProjectBuilder
import com.thoughtworks.qdox.model.JavaClass
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

public class JaxRs2RetrofitTask extends DefaultTask {
    @InputDirectory File inputDir
    @OutputDirectory File outputDir = new File("${project.buildDir}/generated/source/jaxrs2retrofit")
    String retrofitPackageName = "client"
    RetrofitReturnStrategy retrofitReturnStrategy = RetrofitReturnStrategy.BOTH;
    String excludedClassNamesRegex = ""

    @TaskAction
    public void execute(IncrementalTaskInputs inputs) {
        RetrofitGenerator generator = new RetrofitGenerator(
                retrofitReturnStrategy,
                retrofitPackageName,
                excludedClassNamesRegex);
        JavaProjectBuilder builder = new JavaProjectBuilder();
        builder.addSourceTree(inputDir);
        for (JavaClass javaClass : builder.getClasses()) {
            JavaFile javaFile = generator.createResource(javaClass);
            if (javaFile == null) continue;
            javaFile.writeTo(outputDir);
        }
    }

 }
