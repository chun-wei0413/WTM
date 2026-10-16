package com.wtm;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Clean Architecture inside every bounded context, and a few rules between the contexts (decision 29).
 *
 * <pre>
 *   adapter -> usecase -> entity
 * </pre>
 *
 * A context may use another one only through its {@code usecase.port.in} and through {@code shared}.
 */
@AnalyzeClasses(packages = "com.wtm", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String[] CONTEXTS = {"curation", "discovery", "feedback", "account"};

    @ArchTest
    static final ArchRule entity_depends_on_nothing_outside =
            noClasses().that().resideInAPackage("..entity..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..usecase..", "..adapter..", "org.springframework..");

    /**
     * The one framework dependency allowed in a use case is {@code @Transactional}, so a service can load and save
     * an aggregate inside a single database transaction.
     */
    @ArchTest
    static final ArchRule usecase_does_not_depend_on_adapters_or_frameworks =
            noClasses().that().resideInAPackage("..usecase..")
                    .should().dependOnClassesThat(
                            resideInAPackage("..adapter..").or(
                                    resideInAnyPackage("org.springframework..")
                                            .and(not(resideInAPackage("org.springframework.transaction.annotation.."))))
                    );

    @ArchTest
    static final ArchRule entity_and_usecase_do_not_depend_on_config =
            noClasses().that().resideInAnyPackage("..entity..", "..usecase..")
                    .should().dependOnClassesThat().resideInAPackage("com.wtm.config..");

    /** The use cases are the only way in: what a controller or a worker calls is {@code usecase.port.in}. */
    @ArchTest
    static final ArchRule service_is_reached_through_its_use_case_only =
            noClasses().that().resideOutsideOfPackages("..usecase.service..", "com.wtm.config..")
                    .should().dependOnClassesThat().resideInAPackage("..usecase.service..");

    @ArchTest
    static final ArchRule shared_depends_on_no_context =
            noClasses().that().resideInAPackage("com.wtm.shared..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.wtm.curation..", "com.wtm.discovery..", "com.wtm.feedback..", "com.wtm.account..");

    /** A context may use another one only through {@code usecase.port.in} and {@code shared}. */
    @ArchTest
    static final ArchRule curation_uses_no_inside_of_another_context = insideOfOthers("curation");

    @ArchTest
    static final ArchRule discovery_uses_no_inside_of_another_context = insideOfOthers("discovery");

    @ArchTest
    static final ArchRule feedback_uses_no_inside_of_another_context = insideOfOthers("feedback");

    @ArchTest
    static final ArchRule account_uses_no_inside_of_another_context = insideOfOthers("account");

    @ArchTest
    static final ArchRule contexts_have_no_cycles =
            slices().matching("com.wtm.(*)..").should().beFreeOfCycles();

    private static ArchRule insideOfOthers(String context) {
        java.util.List<String> inside = new java.util.ArrayList<>();
        for (String other : CONTEXTS) {
            if (!other.equals(context)) {
                inside.add("com.wtm." + other + ".entity..");
                inside.add("com.wtm." + other + ".adapter..");
                inside.add("com.wtm." + other + ".usecase.port.out..");
                inside.add("com.wtm." + other + ".usecase.service..");
            }
        }
        return noClasses().that().resideInAPackage("com.wtm." + context + "..")
                .should().dependOnClassesThat().resideInAnyPackage(inside.toArray(new String[0]));
    }
}
