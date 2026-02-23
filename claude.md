## Caude Capability Design Guidelines
When designing a Capability :  

# Do's
- Keep the specification as an first class citizen in your context
# Dont's 
- Do not expose a intputParameter if it is not used in any of the steps
- Do not declare consumed outputparameters that are not used in the exposed part
- Do not prefix variables with the capability/namespace/resource name, as they are already scoped to the capability/namespace/resource unless explictly needed for disambiguation.
