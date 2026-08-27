package com.atcsafety.detection;

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
 * Executable architecture rules for the Separation Infringement Detection Service.
 *
 * <p>Eight contracts are enforced across five concerns:
 * <ol>
 *   <li><strong>Domain purity</strong> — {@code domain/} has zero Spring, Kafka, or Spring Data imports.</li>
 *   <li><strong>Layer isolation</strong> — {@code application/} cannot depend on {@code infrastructure/}.</li>
 *   <li><strong>Controller/Repository separation</strong> — no {@code *Controller} accesses a {@code *Repository}.</li>
 *   <li><strong>Constructor injection</strong> — {@code @Autowired} on fields is forbidden; all injection is constructor-based.</li>
 *   <li><strong>Persistence model encapsulation</strong> — {@code @Document}-annotated classes must reside in {@code infrastructure/}.</li>
 *   <li><strong>Package cohesion</strong> — no cyclic package dependencies.</li>
 *   <li><strong>Naming conventions</strong> — {@code *Document} → {@code infrastructure/}; {@code *Event} → {@code domain/}.</li>
 *   <li><strong>Logging policy</strong> — {@code System.out} is forbidden; all output must go through SLF4J.</li>
 * </ol>
 *
 * <p>{@code mvn test} fails fast if any rule is violated, providing a compile-time-equivalent
 * guard for DDD layer contracts without a separate static-analysis step.
 */
class ArchitectureRulesTest {

    private static JavaClasses detectionClasses;

    @BeforeAll
    static void importClasses() {
        detectionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.atcsafety.detection");
    }

    @Nested
    class DomainPurity {

        /**
         * The domain layer must be plain Java — zero dependencies on Spring, Kafka, or Spring Data.
         * This keeps domain logic unit-testable with no framework context and decoupled from
         * infrastructure choices.
         */
        @Test
        void should_not_import_spring_or_kafka_from_domain_package() {
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "org.apache.kafka..",
                            "org.springframework.data..")
                    .check(detectionClasses);
        }
    }

    @Nested
    class LayerIsolation {

        /**
         * Application-layer classes may depend on the domain but must not reach down into
         * infrastructure. Infrastructure adapters are injected by Spring; the application
         * layer coordinates them through domain port interfaces.
         */
        @Test
        void should_not_depend_on_infrastructure_from_application_package() {
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .check(detectionClasses);
        }
    }

    @Nested
    class ControllerRepositorySeparation {

        /**
         * Controllers express REST entry-points and must delegate to service or use-case
         * objects, never to repositories directly. A controller that queries a repository
         * bypasses the domain and makes business logic untestable in isolation.
         *
         * <p>This module has no {@code *Controller} classes today; {@code allowEmptyShould(true)}
         * prevents a false failure while still catching the violation if one is introduced.
         */
        @Test
        void should_not_depend_on_repository_from_controller() {
            noClasses()
                    .that().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                    .allowEmptyShould(true)
                    .check(detectionClasses);
        }
    }

    @Nested
    class ConstructorInjectionEnforcement {

        /**
         * Field injection via {@code @Autowired} is forbidden. Spring resolves field-injected
         * dependencies via reflection, making it impossible to instantiate the class in a unit
         * test without a Spring context. Constructor injection makes the dependency contract
         * explicit and keeps every class independently testable.
         */
        @Test
        void should_not_use_field_injection_with_autowired() {
            noFields()
                    .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    .check(detectionClasses);
        }
    }

    @Nested
    class PersistenceModelEncapsulation {

        /**
         * MongoDB document models carry persistence-specific annotations ({@code @Document},
         * {@code @Id}, collection mapping) and must not escape the infrastructure layer.
         * A {@code @Document} class in {@code application/} or {@code domain/} means the
         * persistence model has leaked into business logic — a DDD boundary violation.
         */
        @Test
        void should_confine_document_models_to_infrastructure() {
            classes()
                    .that().areAnnotatedWith(
                            "org.springframework.data.mongodb.core.mapping.Document")
                    .should().resideInAPackage("..infrastructure..")
                    .check(detectionClasses);
        }
    }

    @Nested
    class PackageCohesion {

        /**
         * Cyclic package dependencies are a primary cause of tight coupling. They prevent
         * independent evolution of packages and make isolated testing harder. ArchUnit
         * detects cycles at the package level before they become structural debt.
         *
         * <p>Slicing on {@code com.atcsafety.detection.(*)..*} creates one slice per
         * immediate sub-package ({@code domain}, {@code application}, {@code infrastructure}).
         * A violation is reported if any two slices form a cycle.
         */
        @Test
        void should_have_no_package_cycles() {
            slices()
                    .matching("com.atcsafety.detection.(*)..")
                    .should().beFreeOfCycles()
                    .check(detectionClasses);
        }
    }

    @Nested
    class NamingConventions {

        /**
         * Classes named {@code *Document} are MongoDB persistence models and must reside in
         * {@code infrastructure/}. The name is a contract with the reader: if you see
         * {@code *Document}, you know it is a persistence artifact and has framework dependencies.
         * Placing it outside infrastructure silently breaks that contract.
         */
        @Test
        void should_confine_document_classes_to_infrastructure_by_name() {
            classes()
                    .that().haveSimpleNameEndingWith("Document")
                    .should().resideInAPackage("..infrastructure..")
                    .check(detectionClasses);
        }

        /**
         * Classes named {@code *Event} are domain aggregates or domain events and must reside
         * in {@code domain/}. Moving an {@code *Event} to {@code application/} or
         * {@code infrastructure/} indicates a DDD boundary violation — business state is
         * being managed outside the domain layer.
         */
        @Test
        void should_confine_event_classes_to_domain_by_name() {
            classes()
                    .that().haveSimpleNameEndingWith("Event")
                    .should().resideInAPackage("..domain..")
                    .check(detectionClasses);
        }
    }

    @Nested
    class LoggingPolicy {

        /**
         * All runtime output must go through SLF4J — never {@code System.out}.
         * In an ATC safety system, log statements carry timestamps, severity levels, and
         * MDC context that regulators and incident investigators rely on.
         * {@code System.out} output bypasses all of that and cannot be audited.
         */
        @Test
        void should_use_slf4j_not_system_out() {
            noClasses()
                    .should().accessField(System.class, "out")
                    .check(detectionClasses);
        }
    }
}
