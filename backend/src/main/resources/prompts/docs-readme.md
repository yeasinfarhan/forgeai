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

Write the project's README.md (it sits at the repository root; the other documents are relative to it).

Include, in this order, leaving out a section only when there is nothing true to say:
1. The project name as the title and a short summary of what it does and for whom.
2. Features: the main capabilities, from the approved functional requirements.
3. Tech stack: the technologies from the architecture and the code.
4. Getting started: prerequisites, configuration (environment variables from the example configuration, with what each is for) and how to build and run the project, exactly as the code supports it.
5. Running the tests, if the project has tests.
6. Project structure: the main folders and what they contain.
7. Documentation: links to the documents in <documents>, one line each.
