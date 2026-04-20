"use client";

import { useTranslations } from "next-intl";
import { AppHeader } from "@/components/layout/app-header";
import { useGetMyProfile } from "@/generated/auth";
import { AvatarUpload } from "@/features/profile/components/avatar-upload";
import { ProfileForm } from "@/features/profile/components/profile-form";
import { PreferencesForm } from "@/features/profile/components/preferences-form";
import { Skeleton } from "@/components/ui/skeleton";

function ProfilePageSkeleton() {
  return (
    <div className="flex flex-1 flex-col gap-6 p-4 md:p-6 max-w-2xl">
      <Skeleton className="h-8 w-48" />
      <Skeleton className="h-48 w-full rounded-xl" />
      <Skeleton className="h-64 w-full rounded-xl" />
      <Skeleton className="h-48 w-full rounded-xl" />
    </div>
  );
}

export default function ProfilePage() {
  const t = useTranslations("profile");
  const { data: profileData, isLoading } = useGetMyProfile();
  const profile = profileData?.status === 200 ? profileData.data : undefined;

  if (isLoading) {
    return (
      <>
        <AppHeader mode="list" title={t("title")} />
        <ProfilePageSkeleton />
      </>
    );
  }

  return (
    <>
      <AppHeader mode="list" title={t("title")} />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6 max-w-2xl">
        <AvatarUpload profile={profile} />
        <ProfileForm profile={profile} />
        <PreferencesForm />
      </div>
    </>
  );
}
