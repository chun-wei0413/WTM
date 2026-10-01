package com.memehub;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Clean Architecture: dependencies point inward only.
 * adapter -> application -> domain
 */
@AnalyzeClasses(packages = "com.memehub", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_depends_on_nothing_outside =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..application..", "..adapter..", "org.springframework..");

    /**
     * The one framework dependency allowed in the application layer is
     * {@code @Transactional}, so a command handler can load and save an
     * aggregate inside a single database transaction.
     */
    @ArchTest
    static final ArchRule application_does_not_depend_on_adapters_or_frameworks =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat(
                            resideInAPackage("..adapter..").or(
                                    resideInAnyPackage("org.springframework..")
                                            .and(not(resideInAPackage("org.springframework.transaction.annotation.."))))
                    );

    @ArchTest
    static final ArchRule domain_and_application_do_not_depend_on_config =
            noClasses().that().resideInAnyPackage("..domain..", "..application..")
                    .should().dependOnClassesThat().resideInAPackage("com.memehub.config..");
}
