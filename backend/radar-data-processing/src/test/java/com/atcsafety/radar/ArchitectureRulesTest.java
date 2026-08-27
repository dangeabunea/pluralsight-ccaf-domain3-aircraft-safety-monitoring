package com.atcsafety.radar;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Executable architecture rules for the Radar Data Processing Service.
 *
 * <p>Eight contracts are enforced. This module currently has no {@code domain/} or
 * {@code infrastructure/} packages, so rules that reference those packages use
 * {@code allowEmptyShould(true)} to remain green today while guarding against
 * violations if those packages are added later without honouring the same contracts.
 *
 * <p>{@code mvn test} fails fast if any rule is violated.
 */
class ArchitectureRulesTest {

    private static JavaClasses radarClasses;

    @BeforeAll
    static void importClasses() {
        radarClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.atcsafety.radar");
    }

    @Nested
    class DomainPurity {

        /**
         * The domain layer must be plain Java — zero dependencies on Spring, Kafka, or Spring Data.
         * This module has no {@code domain/} package today; {@code allowEmptyShould(true)} prevents
         * a false failure while still catching the violation if a {@code domain/} package is added
         * without honouring the purity contract.
         */
        @Test
        void should_not_import_spring_or_kafka_from_domain_package() {
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "org.apache.kafka..",
                            "org.springframework.data..")
                    .allowEmptyShould(true)
                    .check(radarClasses);
        }
    }

    @Nested
    class LayerIsolation {

        /**
         * Application-layer classes must not reach into infrastructure.
         * This module has no {@code infrastructure/} package today; {@code allowEmptyShould(true)}
         * prevents a false failure while still catching the violation if both packages are added.
         */
        @Test
        void should_not_depend_on_infrastructure_from_application_package() {
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .allowEmptyShould(true)
                    .check(radarClasses);
        }
    }

    @Nested
    class ControllerRepositorySeparation {

        /**
         * Controllers must not access repositories directly.
         * This module has no {@code *Controller} classes today; {@code allowEmptyShould(true)}
         * prevents a false failure while still catching violations if a Controller is introduced.
         */
        @Test
        void should_not_depend_on_repository_from_controller() {
            noClasses()
                    .that().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                    .allowEmptyShould(true)
                    .check(radarClasses);
        }
    }

    @Nested
    class ConstructorInjectionEnforcement {

        /**
         * Field injection via {@code @Autowired} is forbidden. Constructor injection makes the
         * dependency contract explicit and keeps every class independently testable without a
         * Spring context.
         */
        @Test
        void should_not_use_field_injection_with_autowired() {
            noFields()
                    .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    .check(radarClasses);
        }
    }

    @Nested
    class PersistenceModelEncapsulation {

        /**
         * MongoDB {@code @Document} classes must reside in {@code infrastructure/}.
         * This module has no MongoDB dependency today; {@code allowEmptyShould(true)} prevents
         * a false failure while guarding against the violation if persistence is added later.
         */
        @Test
        void should_confine_document_models_to_infrastructure() {
            classes()
                    .that().areAnnotatedWith(
                            "org.springframework.data.mongodb.core.mapping.Document")
                    .should().resideInAPackage("..infrastructure..")
                    .allowEmptyShould(true)
                    .check(radarClasses);
        }
    }

    @Nested
    class PackageCohesion {

        /**
         * Cyclic package dependencies are a primary cause of tight coupling.
         * Slicing on {@code com.atcsafety.radar.(*)..*} creates one slice per immediate
         * sub-package ({@code application}, {@code loading}, {@code validation}, {@code enrichment}).
         * A violation is reported if any two slices form a cycle.
         */
        @Test
        void should_have_no_package_cycles() {
            slices()
                    .matching("com.atcsafety.radar.(*)..")
                    .should().beFreeOfCycles()
                    .check(radarClasses);
        }
    }

    @Nested
    class NamingConventions {

        /**
         * Classes named {@code *Document} are MongoDB persistence models and must reside in
         * {@code infrastructure/}. This module has no {@code *Document} classes today.
         */
        @Test
        void should_confine_document_classes_to_infrastructure_by_name() {
            classes()
                    .that().haveSimpleNameEndingWith("Document")
                    .should().resideInAPackage("..infrastructure..")
                    .allowEmptyShould(true)
                    .check(radarClasses);
        }

        /**
         * Classes named {@code *Event} are domain aggregates or domain events and must reside
         * in {@code domain/}. This module has no {@code *Event} classes today.
         */
        @Test
        void should_confine_event_classes_to_domain_by_name() {
            classes()
                    .that().haveSimpleNameEndingWith("Event")
                    .should().resideInAPackage("..domain..")
                    .allowEmptyShould(true)
                    .check(radarClasses);
        }
    }

    @Nested
    class LoggingPolicy {

        /**
         * All runtime output must go through SLF4J — never {@code System.out}.
         * {@code System.out} output bypasses log levels, timestamps, and MDC context.
         */
        @Test
        void should_use_slf4j_not_system_out() {
            noClasses()
                    .should().accessField(System.class, "out")
                    .check(radarClasses);
        }
    }
}
