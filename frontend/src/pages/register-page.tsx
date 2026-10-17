import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";

import { useAuth } from "@/auth/auth-context";
import { FormError, FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { ApiError, errorMessage } from "@/lib/api";

const MIN_PASSWORD_LENGTH = 8;

type Field = "displayName" | "email" | "password";
type FieldErrors = Partial<Record<Field, string>>;

function validate(displayName: string, email: string, password: string): FieldErrors {
  const errors: FieldErrors = {};
  if (!displayName.trim()) errors.displayName = "Enter your name.";
  if (!/^\S+@\S+\.\S+$/.test(email.trim())) errors.email = "Enter a valid email address.";
  if (password.length < MIN_PASSWORD_LENGTH) errors.password = `Use at least ${MIN_PASSWORD_LENGTH} characters.`;
  return errors;
}

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    const errors = validate(displayName, email, password);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setIsSubmitting(true);
    try {
      await register({ displayName: displayName.trim(), email: email.trim(), password });
      navigate("/", { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.violations.length > 0) {
        const serverErrors: FieldErrors = {};
        for (const violation of err.violations) {
          const field = violation.field === "passwordWithinBcryptLimit" ? "password" : violation.field;
          if (field === "displayName" || field === "email" || field === "password") {
            serverErrors[field] = violation.message;
          }
        }
        setFieldErrors(serverErrors);
      } else {
        setError(errorMessage(err));
      }
      setIsSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl">Create your account</CardTitle>
        <CardDescription>Start turning ideas into working software.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <FormError message={error} />
          <FormField id="displayName" label="Name" error={fieldErrors.displayName}>
            <Input
              id="displayName"
              autoComplete="name"
              maxLength={100}
              value={displayName}
              aria-invalid={Boolean(fieldErrors.displayName)}
              aria-describedby={fieldErrors.displayName ? "displayName-error" : undefined}
              onChange={(event) => setDisplayName(event.target.value)}
            />
          </FormField>
          <FormField id="email" label="Email" error={fieldErrors.email}>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              maxLength={320}
              value={email}
              aria-invalid={Boolean(fieldErrors.email)}
              aria-describedby={fieldErrors.email ? "email-error" : undefined}
              onChange={(event) => setEmail(event.target.value)}
            />
          </FormField>
          <FormField
            id="password"
            label="Password"
            error={fieldErrors.password}
            hint={`At least ${MIN_PASSWORD_LENGTH} characters.`}
          >
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              maxLength={72}
              value={password}
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={fieldErrors.password ? "password-error" : undefined}
              onChange={(event) => setPassword(event.target.value)}
            />
          </FormField>
          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? "Creating account…" : "Create account"}
          </Button>
        </form>
        <p className="mt-6 text-center text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Sign in
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
