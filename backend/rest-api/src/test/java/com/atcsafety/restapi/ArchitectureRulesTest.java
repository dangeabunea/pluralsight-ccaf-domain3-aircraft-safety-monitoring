package com.atcsafety.restapi;

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
 * Executable architecture rules for the REST API Service.
 *
 * <p>This module has no production code yet. All rules use {@code allowEmptyShould(true)}
 * to remain green today while acting as forward guards — violations will be caught
 * automatically as soon as code is written that breaks a contract.
 *
 * <p>{@code mvn test} fails fast if any rule is violated.
 */
class ArchitectureRulesTest {

    private static JavaClasses restApiClasses;

    @BeforeAll
    static void importClasses() {
        restApiClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.atcsafety.restapi");
    }

    @Nested
    class DomainPurity {

        /**
         * The domain layer must be plain Java — zero dependencies on Spring, Kafka, or Spring Data.
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
                    .check(restApiClasses);
        }
    }

    @Nested
    class LayerIsolation {

        /**
         * Application-layer classes must not reach into infrastructure.
         */
        @Test
        void should_not_depend_on_infrastructure_from_application_package() {
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .allowEmptyShould(true)
                    .check(restApiClasses);
        }
    }

    @Nested
    class ControllerRepositorySeparation {

        /**
         * Controllers must not access repositories directly.
         */
        @Test
        void should_not_depend_on_repository_from_controller() {
            noClasses()
                    .that().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                    .allowEmptyShould(true)
                    .check(restApiClasses);
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
                    .allowEmptyShould(true)
                    .check(restApiClasses);
        }
    }

    @Nested
    class PersistenceModelEncapsulation {

        /**
         * MongoDB {@code @Document} classes must reside in {@code infrastructure/}.
         */
        @Test
        void should_confine_document_models_to_infrastructure() {
            classes()
                    .that().areAnnotatedWith(
                            "org.springframework.data.mongodb.core.mapping.Document")
                    .should().resideInAPackage("..infrastructure..")
                    .allowEmptyShould(true)
                    .check(restApiClasses);
        }
    }

    @Nested
    class PackageCohesion {

        /**
         * No cyclic package dependencies. Slicing on {@code com.atcsafety.restapi.(*)..*}
         * creates one slice per immediate sub-package. A violation is reported if any two slices
         * form a cycle.
         */
        @Test
        void should_have_no_package_cycles() {
            slices()
                    .matching("com.atcsafety.restapi.(*)..")
                    .should().beFreeOfCycles()
                    .allowEmptyShould(true)
                    .check(restApiClasses);
        }
    }

    @Nested
    class NamingConventions {

        /**
         * Classes named {@code *Document} are MongoDB persistence models and must reside in
         * {@code infrastructure/}.
         */
        @Test
        void should_confine_document_classes_to_infrastructure_by_name() {
            classes()
                    .that().haveSimpleNameEndingWith("Document")
                    .should().resideInAPackage("..infrastructure..")
                    .allowEmptyShould(true)
                    .check(restApiClasses);
        }

        /**
         * Classes named {@code *Event} are domain aggregates or domain events and must reside
         * in {@code domain/}.
         */
        @Test
        void should_confine_event_classes_to_domain_by_name() {
            classes()
                    .that().haveSimpleNameEndingWith("Event")
                    .should().resideInAPackage("..domain..")
                    .allowEmptyShould(true)
                    .check(restApiClasses);
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
                    .allowEmptyShould(true)
                    .check(restApiClasses);
        }
    }
}
