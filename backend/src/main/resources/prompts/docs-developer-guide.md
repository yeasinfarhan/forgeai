<project>
{{projectName}}
</project>

<description>
{{projectDescription}}
</description>

<requirements>
{{requirements}}
</requirements>

<architecture>
{{architecture}}
</architecture>

<database>
{{database}}
</database>

<api>
{{api}}
</api>

<documents>
{{documents}}
</documents>

<workspace>
{{workspace}}
</workspace>

<files>
{{files}}
</files>

Write the developer guide (docs/developer-guide.md; other documents are relative to the repository root, so link to them as "../README.md" or by file name for those in docs/).

It is for a developer joining the project. Include:
1. Architecture in practice: the style, the components and where each one lives in the code.
2. Project layout: the folders and the role of the main files, and the layering or conventions the code follows (naming, packages, error handling, validation, configuration).
3. Data model: how the tables map to the code (entities, repositories, migrations), with a link to data-model.md for details.
4. API conventions: base path, authentication, error format and where endpoints are implemented, with a link to api-reference.md.
5. Development workflow: setting up a local environment, running the application and the tests, as the code supports it.
6. Adding a feature: the concrete steps, following the patterns of the existing code, with the files a typical change touches.
7. Known gaps: parts of the design that have no code yet, if any (compare the design with the files listed).
