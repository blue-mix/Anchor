import re

with open('README.md', 'r') as f:
    readme_content = f.read()

# Replace the Project Structure section
old_structure = """## Project Structure
```
app/src/main/java/com/example/anchor/
├── AnchorApp.kt                  # Application class
├── MainActivity.kt               # Entry point
├── core/                         # Core utilities and extensions
│   ├── extension/
│   ├── result/
│   └── util/
├── data/                         # Data layer (DTOs, Mappers, Repositories)
│   ├── dto/
│   ├── mapper/
│   ├── model/
│   ├── repository/
│   └── source/
├── di/                           # Dependency Injection setup (Koin)
├── domain/                       # Domain layer (Models, Repositories, Use Cases)
│   ├── model/
│   ├── repository/
│   └── usecase/
├── server/                       # Ktor HTTP Server and DLNA
│   ├── AnchorHttpServer.kt
│   ├── DeviceDescriptionParser.kt
│   ├── UpnpDiscoveryManager.kt
│   ├── dlna/
│   ├── handler/
│   ├── routing/
│   └── service/
└── ui/                           # Jetpack Compose UI (Screens, ViewModels)
    ├── browser/
    ├── components/
    ├── dashboard/
    ├── onboarding/
    ├── player/
    └── theme/
```"""

new_structure = """## Project Structure
```
app/src/main/java/com/example/anchor/
├── core/                         # Core utilities and extensions
│   ├── extension/
│   ├── result/
│   └── util/
├── data/                         # Data layer (Repositories, Data Sources, DTOs, Mappers)
│   ├── dto/
│   ├── mapper/
│   ├── model/
│   ├── repository/
│   ├── server/                   # Ktor HTTP Server and DLNA (API/Data Source)
│   └── source/
├── di/                           # Dependency Injection setup (Koin)
├── domain/                       # Domain layer (Pure business logic: UseCases, Entities/Models, Repository Interfaces)
│   ├── model/
│   ├── repository/
│   └── usecase/
└── presentation/                 # Presentation layer (UI: Jetpack Compose, ViewModels, MainActivity)
    ├── AnchorApp.kt              # Application class
    ├── MainActivity.kt           # Entry point
    ├── browser/
    ├── components/
    ├── dashboard/
    ├── onboarding/
    ├── player/
    └── theme/
```

### Clean Architecture Layers

**Data Layer (`data/`)**
Responsible for data retrieval and submission. It implements the repository interfaces defined in the Domain layer. This layer contains Data Sources (like the Ktor HTTP Server, local file system, SharedPreferences), DTOs (Data Transfer Objects), and Mappers to convert between Data and Domain models. It has dependencies on external frameworks but no dependencies on the Presentation layer.

**Domain Layer (`domain/`)**
The core of the application containing pure business logic. It defines the application's Entities (Models) and UseCases. It also defines Repository Interfaces. This layer is completely isolated and must not have any dependencies on Android frameworks, the Data layer, or the Presentation layer.

**Presentation Layer (`presentation/`)**
Responsible for the user interface. It contains Jetpack Compose Composables, Activities, and ViewModels. It interacts with the Domain layer by executing UseCases. It has no direct dependencies on the Data layer."""

readme_content = readme_content.replace(old_structure, new_structure)

with open('README.md', 'w') as f:
    f.write(readme_content)
