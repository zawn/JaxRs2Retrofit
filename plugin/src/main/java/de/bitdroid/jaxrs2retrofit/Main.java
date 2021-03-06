package de.bitdroid.jaxrs2retrofit;

import com.squareup.javapoet.JavaFile;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;

import java.io.File;

import de.bitdroid.jaxrs2retrofit.converter.ParamConverterManager;

public final class Main {

	private static final Options commandLineOptions = new Options();
	private static final String
			OPTION_SOURCE = "src",
			OPTION_EXCLUDED_CLASSES = "exclude";

	static {
		commandLineOptions.addOption(OPTION_SOURCE, true, "JAX RS Java input files");
		commandLineOptions.addOption(OptionBuilder.hasArg(true).isRequired(false).withArgName("arg").withDescription("Regex to exclude classes").create(OPTION_EXCLUDED_CLASSES));
	}


	public static void main(String[] args) throws Exception {
		CommandLine commandLine = new BasicParser().parse(commandLineOptions, args);
		if (!commandLine.hasOption(OPTION_SOURCE)) {
			printHelp();
			return;
		}

		File inputFile = new File(commandLine.getOptionValue(OPTION_SOURCE));
		if (!inputFile.exists()) {
			printHelp();
			return;
		}

		String excludedClassNamesRegex = "";
		if (commandLine.hasOption(OPTION_EXCLUDED_CLASSES)) excludedClassNamesRegex = commandLine.getOptionValue(OPTION_EXCLUDED_CLASSES);

		RetrofitGenerator generator = new RetrofitGenerator(
				new GeneratorSettings(
						"client",
						excludedClassNamesRegex,
						true,
						true,
						true,
						ParamConverterManager.getDefaultInstance()));

		JavaProjectBuilder builder = new JavaProjectBuilder();
		builder.addSourceTree(inputFile);

		for (JavaClass javaClass : builder.getClasses()) {
			JavaFile javaFile = generator.createResource(javaClass);
			if (javaFile == null) continue;
			javaFile.writeTo(System.out);
		}
	}


	private static void printHelp() {
		HelpFormatter helpFormatter = new HelpFormatter();
		helpFormatter.printHelp(Main.class.getSimpleName(), commandLineOptions);
	}

}
