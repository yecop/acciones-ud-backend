package co.edu.udistrital.accionesud.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "co.edu.udistrital.accionesud",
        importOptions = ImportOption.DoNotIncludeTests.class)
public class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule dominioNoDependeDeSpring = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .because("el dominio debe ser independiente del framework");

    @ArchTest
    static final ArchRule dominioNoDependeDeAdaptadores = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..adapter..")
            .because("el dominio no debe conocer adaptadores ni infraestructura");

    @ArchTest
    static final ArchRule dominioNoDependeDeAplicacion = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..")
            .because("el dominio es el nucleo y no debe depender de casos de uso");

    @ArchTest
    static final ArchRule dominioSoloDependeDeJavaYDelDominio = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..")
            .because("el dominio solo puede usar el lenguaje y sus propias abstracciones");

    @ArchTest
    static final ArchRule aplicacionNoDependeDeSpringNiInfraestructura = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "..infrastructure..", "..adapter..")
            .because("la aplicacion debe orquestar el dominio sin acoplarse a tecnologia")
            .allowEmptyShould(true);
}
