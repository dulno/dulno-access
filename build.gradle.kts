plugins {
  id("java")
  id("maven-publish")
  id("io.freefair.lombok") version "9.8.0"
}

group = "com.dulno"
version = "1.0.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21
java.targetCompatibility = JavaVersion.VERSION_21

publishing {
  publications {
    create<MavenPublication>("library") {
      from(components["java"])
    }
  }
}

repositories {
  mavenCentral()
  maven("https://jitpack.io")
  mavenLocal()
}

dependencies {
  testCompileOnly(platform("org.junit:junit-bom:6.0.1"))
  testCompileOnly("org.junit.jupiter:junit-jupiter:6.1.3")

  compileOnly("com.dulno:core:1.0.0-SNAPSHOT")

  compileOnly("com.google.inject:guice:7.0.0")

  compileOnly("com.google.guava:guava:33.5.0-jre")

  compileOnly("org.projectlombok:lombok:1.18.42")
  annotationProcessor("org.projectlombok:lombok:1.18.42")
  testCompileOnly("org.projectlombok:lombok:1.18.42")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.42")

  compileOnly("com.datastax.oss:java-driver-core:4.17.0")

  compileOnly("org.json:json:20260814")
  compileOnly("commons-io:commons-io:2.21.0")

  compileOnly("org.springframework.boot:spring-boot-starter-web:4.1.1")

  compileOnly("io.jsonwebtoken:jjwt:0.13.0")

  compileOnly("com.sun.mail:javax.mail:1.6.2")

  compileOnly("com.stripe:stripe-java:29.1.0")

  compileOnly("com.maxmind.geoip2:geoip2:5.2.0")

  compileOnly("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20261011.1")

  implementation("com.google.api-client:google-api-client:2.8.1")

  implementation("com.nimbusds:nimbus-jose-jwt:10.6")
}

tasks.test {
  useJUnitPlatform()
}

tasks.jar {
  val dependencies = configurations.runtimeClasspath.get().map(::zipTree)
  from(dependencies)
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}