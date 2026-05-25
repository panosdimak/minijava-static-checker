import java.io.FileInputStream;
import java.io.FileNotFoundException;

import symboltable.STValidator;
import symboltable.SemanticError;
import syntaxtree.*;

public class Main {

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: java Main <inputFile>");
			System.exit(1);
		}

		for (String path : args) {
			System.err.println("=== " + path + " ===");

			try (FileInputStream fis = new FileInputStream(path)) {
				MiniJavaParser parser = new MiniJavaParser(fis);

				Goal root = parser.Goal();

				STBuilder stb = new STBuilder();
				root.accept(stb);

				STValidator stv = new STValidator();
				stv.resolveTypes(stb.globalTable);
				stv.checkOverloads(stb.globalTable);


				System.err.println("Program parsed successfully.");
			} catch (ParseException e) {
				System.err.println(e.getMessage());
			} catch (FileNotFoundException e) {
				System.err.println(e.getMessage());
			} catch (SemanticError e) {
				System.err.println(e.getMessage());
			} catch (Exception e) {
				System.err.println(e.getMessage());
			}
		}
	}
}
