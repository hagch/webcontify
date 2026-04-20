"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Loader2, KeyRound } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import {
  useUpdateProfile,
  useGetAccountUrl,
  getGetMyProfileQueryKey,
  type MyProfileResponse,
} from "@/generated/auth";
import {
  updateProfileSchema,
  type UpdateProfileFormValues,
} from "../schemas/profile";

interface ProfileFormProps {
  profile: MyProfileResponse | undefined;
}

export function ProfileForm({ profile }: ProfileFormProps) {
  const t = useTranslations("profile");
  const queryClient = useQueryClient();
  const updateProfile = useUpdateProfile();
  const { data: accountUrlData } = useGetAccountUrl();
  const accountUrl = accountUrlData?.status === 200 ? accountUrlData.data.url : undefined;

  const form = useForm<UpdateProfileFormValues>({
    resolver: zodResolver(updateProfileSchema),
    defaultValues: {
      firstName: "",
      lastName: "",
    },
  });

  useEffect(() => {
    if (profile) {
      form.reset({
        firstName: profile.firstName,
        lastName: profile.lastName,
      });
    }
  }, [profile, form]);

  async function onSubmit(values: UpdateProfileFormValues) {
    try {
      const response = await updateProfile.mutateAsync({ data: values });
      if (response.status === 200) {
        toast.success(t("name.saved"));
        queryClient.invalidateQueries({ queryKey: getGetMyProfileQueryKey() });
      }
    } catch {
      toast.error(t("name.error"));
    }
  }

  return (
    <Card data-testid="profile-form">
      <CardHeader>
        <CardTitle>{t("name.title")}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="firstName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("name.firstName")}</FormLabel>
                  <FormControl>
                    <Input
                      data-testid="profile-firstname"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="lastName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("name.lastName")}</FormLabel>
                  <FormControl>
                    <Input
                      data-testid="profile-lastname"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <Button
              data-testid="profile-save"
              type="submit"
              aria-busy={updateProfile.isPending}
            >
              {updateProfile.isPending && (
                <Loader2 className="mr-2 size-4 animate-spin" />
              )}
              {t("name.save")}
            </Button>
          </form>
        </Form>

        {accountUrl && (
          <>
            <Separator />
            <div className="space-y-2">
              <h3 className="text-sm font-medium">{t("password.title")}</h3>
              <p className="text-sm text-muted-foreground">
                {t("password.description")}
              </p>
              <Button
                data-testid="password-change-button"
                type="button"
                variant="outline"
                size="sm"
                onClick={() => window.open(accountUrl, "_blank")}
              >
                <KeyRound className="mr-2 size-4" />
                {t("password.change")}
              </Button>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}
