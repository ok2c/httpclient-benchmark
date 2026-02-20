plugins {
    id("java")
}

group = "com.github.ok2c.benchmark"
version = "1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.commons.cli)
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.nop)
    implementation(libs.httpclient5)
    implementation(libs.jetty)
    implementation(libs.okhttp)
    implementation(libs.spring.webflux2) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.test {
    useJUnitPlatform()
}

val targetUri = project.findProperty("benchmark.target-uri")?.toString()
val n = project.findProperty("benchmark.requests")?.toString()
val c = project.findProperty("benchmark.concurrency")?.toString()
val content = project.findProperty("benchmark.content-file")?.toString()
val contentType = project.findProperty("benchmark.content-type")?.toString()

interface Support {
    @get:Inject val execOperations: ExecOperations
}

if (targetUri != null) {

    project.tasks.register("benchmark", {

        val support = project.objects.newInstance<Support>()

        doLast {
            val agents = listOf(
                "JREHttpUrlConnection",
                "ApacheHttpClientV5",
                "JRE11HttpClient",
                "JettyHttpClientV11",
                "OkHttpClientV4",
                "SpringWebFluxV2",
                "ApacheHttpAsyncClientV5"
            )

            val params = mutableListOf("-n", n ?: "2000", "-c", c ?: "2", "-k")
            if (content != null) {
                params.addAll(listOf("-p", content, "-t", contentType ?: "text/plain"))
            }
            params.add(targetUri)

            for (agent in agents) {
                val execResult = support.execOperations.javaexec {
                    mainClass = "com.ok2c.http.client.benchmark.${agent}"
                    classpath = sourceSets.get("main").runtimeClasspath
                    args = params
                }
                execResult.assertNormalExitValue()
                Thread.sleep(2000)
            }
        }
    })

}