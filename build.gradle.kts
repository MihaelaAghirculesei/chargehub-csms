plugins {
    // Loads the convention plugin once in the root classloader. Without this, modules that apply
    // extra plugins (Spring Boot) get their own copy, and Spotless cannot share its build service
    // between sibling projects.
    id("chargehub.java-conventions") apply false
}
