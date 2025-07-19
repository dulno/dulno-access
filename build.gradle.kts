plugins {
  id("java")
  id("maven-publish")
  id("io.freefair.lombok") version "8.14"
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
  repositories {
    maven {
      url = uri("https://git.dulno.com/api/v4/projects/52/packages/maven")
      credentials(HttpHeaderCredentials::class) {
        name = "Private-Token"
        value = System.getenv("DULNO_GITLAB_PRIVATE_TOKEN") ?:
          findProperty("dulnoGitlabPrivateToken") as String?
      }
      authentication {
        create("header", HttpHeaderAuthentication::class)
      }
    }
  }
}

repositories {
  mavenCentral()
  maven("https://jitpack.io")
  maven {
    url = uri("https://git.dulno.com/api/v4/projects/51/packages/maven")
    credentials(HttpHeaderCredentials::class) {
      name = "Private-Token"
      value = System.getenv("DULNO_GITLAB_PRIVATE_TOKEN") ?:
        findProperty("dulnoGitlabPrivateToken") as String?
    }
    authentication {
      create("header", HttpHeaderAuthentication::class)
    }
  }
}

dependencies {
  testCompileOnly(platform("org.junit:junit-bom:5.13.3"))
  testCompileOnly("org.junit.jupiter:junit-jupiter:5.13.3")

  compileOnly("com.dulno:core:1.0.0-SNAPSHOT")

  compileOnly("com.google.inject:guice:7.0.0")

  compileOnly("com.google.guava:guava:33.4.8-jre")

  compileOnly("org.projectlombok:lombok:1.18.38")
  annotationProcessor("org.projectlombok:lombok:1.18.38")
  testCompileOnly("org.projectlombok:lombok:1.18.38")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.38")

  compileOnly("com.datastax.oss:java-driver-core:4.17.0")

  compileOnly("org.json:json:20250517")
  compileOnly("commons-io:commons-io:2.19.0")

  compileOnly("org.springframework.boot:spring-boot-starter-web:3.5.3")

  compileOnly("io.jsonwebtoken:jjwt:0.12.6")

  compileOnly("com.sun.mail:javax.mail:1.6.2")

  compileOnly("com.stripe:stripe-java:29.1.0")

  compileOnly("com.maxmind.geoip2:geoip2:4.3.1")

  compileOnly("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20240325.1")

  implementation("com.google.api-client:google-api-client:2.8.0")

  implementation("com.nimbusds:nimbus-jose-jwt:10.4")
}

tasks.test {
  useJUnitPlatform()
}

tasks.jar {
  val dependencies = configurations.runtimeClasspath.get().map(::zipTree)
  from(dependencies)
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}