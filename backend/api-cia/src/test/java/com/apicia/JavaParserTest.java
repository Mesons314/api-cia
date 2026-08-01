package com.apicia;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Paths;

public class JavaParserTest {
    @Test
    public void testParseClinicRepo() {
        try {
            System.out.println("Language level before: " + StaticJavaParser.getParserConfiguration().getLanguageLevel());
            StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
            System.out.println("Language level after setting to JAVA_21: " + StaticJavaParser.getParserConfiguration().getLanguageLevel());
            
            StaticJavaParser.parse(Paths.get("C:\\healQueue\\HealQueue\\backend\\src\\main\\java\\com\\HealQueue\\CLINIC\\Repository\\ClinicRepo.java"));
            System.out.println("PARSED ClinicRepo.java SUCCESSFULLY WITH JAVA 21!");
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        
        try {
            StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
            StaticJavaParser.parse(Paths.get("C:\\healQueue\\HealQueue\\backend\\src\\main\\java\\com\\HealQueue\\CLINIC\\Repository\\ClinicRepo.java"));
            System.out.println("PARSED ClinicRepo.java SUCCESSFULLY WITH JAVA 11!");
        } catch (Exception e) {
            System.out.println("FAILED WITH JAVA 11:");
            e.printStackTrace(System.out);
        }
    }
}
