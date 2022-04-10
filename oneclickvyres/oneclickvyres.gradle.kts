version = "1.0.01"

project.extra["PluginName"] = "One Click Vyres"
project.extra["PluginDescription"] = "Pickpocket vyres and bank for food"
project.extra["ProjectSupportUrl"] = "https://github.com/Slibbon/Plugins"

tasks {
    jar {
        manifest {
            attributes(mapOf(
                    "Plugin-Version" to project.version,
                    "Plugin-Id" to nameToId(project.extra["PluginName"] as String),
                    "Plugin-Provider" to project.extra["PluginProvider"],
                    "Plugin-Description" to project.extra["PluginDescription"],
                    "Plugin-License" to project.extra["PluginLicense"]
            ))
        }
    }
}
