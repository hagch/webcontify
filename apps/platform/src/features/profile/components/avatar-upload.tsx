"use client";

import { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Loader2, Upload, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  useUploadAvatar,
  useDeleteAvatar,
  useGetAvatar,
  getGetMyProfileQueryKey,
  getGetAvatarQueryKey,
  type MyProfileResponse,
} from "@/generated/auth";

const MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB
const ALLOWED_TYPES = ["image/jpeg", "image/png"];

interface AvatarUploadProps {
  profile: MyProfileResponse | undefined;
}

export function AvatarUpload({ profile }: AvatarUploadProps) {
  const t = useTranslations("profile");
  const fileInputRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();
  const uploadAvatar = useUploadAvatar();
  const deleteAvatar = useDeleteAvatar();
  const { data: avatarData, refetch: refetchAvatar } = useGetAvatar({
    query: { enabled: !!profile?.hasAvatar },
  });
  const avatarUrl = avatarData?.status === 200 ? avatarData.data.avatarUrl : undefined;

  const initials = profile
    ? `${profile.firstName?.[0] ?? ""}${profile.lastName?.[0] ?? ""}`.toUpperCase() || "U"
    : "U";

  function handleUploadClick() {
    fileInputRef.current?.click();
  }

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!ALLOWED_TYPES.includes(file.type)) {
      toast.error(t("avatar.invalidType"));
      e.target.value = "";
      return;
    }

    if (file.size > MAX_FILE_SIZE) {
      toast.error(t("avatar.tooLarge"));
      e.target.value = "";
      return;
    }

    try {
      const response = await uploadAvatar.mutateAsync({ data: { file } });
      if (response.status === 200) {
        toast.success(t("avatar.uploadSuccess"));
        queryClient.invalidateQueries({ queryKey: getGetMyProfileQueryKey() });
        queryClient.invalidateQueries({ queryKey: getGetAvatarQueryKey() });
      }
    } catch {
      toast.error(t("avatar.error"));
    } finally {
      e.target.value = "";
    }
  }

  async function handleDelete() {
    try {
      const response = await deleteAvatar.mutateAsync();
      if (response.status === 204) {
        toast.success(t("avatar.deleteSuccess"));
        queryClient.removeQueries({ queryKey: getGetAvatarQueryKey() });
        queryClient.invalidateQueries({ queryKey: getGetMyProfileQueryKey() });
      }
    } catch {
      toast.error(t("avatar.error"));
    }
  }

  const isLoading = uploadAvatar.isPending || deleteAvatar.isPending;

  return (
    <Card data-testid="avatar-upload">
      <CardHeader>
        <CardTitle>{t("avatar.title")}</CardTitle>
        <CardDescription>{t("avatar.maxSize")}</CardDescription>
      </CardHeader>
      <CardContent className="flex items-center gap-4">
        <Avatar key={String(!!avatarUrl)} className="h-16 w-16 rounded-lg">
          {avatarUrl && (
            <AvatarImage
              src={avatarUrl}
              alt={initials}
            />
          )}
          <AvatarFallback className="rounded-full text-lg">
            {initials}
          </AvatarFallback>
        </Avatar>

        <div className="flex gap-2">
          <input
            ref={fileInputRef}
            data-testid="avatar-upload-input"
            type="file"
            accept="image/jpeg,image/png"
            className="hidden"
            onChange={handleFileChange}
            aria-label={t("avatar.upload")}
          />
          <Button
            data-testid="avatar-upload-button"
            type="button"
            variant="outline"
            size="sm"
            onClick={handleUploadClick}
            disabled={isLoading}
            aria-busy={uploadAvatar.isPending}
          >
            {uploadAvatar.isPending ? (
              <Loader2 className="mr-2 size-4 animate-spin" />
            ) : (
              <Upload className="mr-2 size-4" />
            )}
            {t("avatar.upload")}
          </Button>

          {profile?.hasAvatar && (
            <Button
              data-testid="avatar-delete"
              type="button"
              variant="outline"
              size="sm"
              onClick={handleDelete}
              disabled={isLoading}
              aria-busy={deleteAvatar.isPending}
            >
              {deleteAvatar.isPending ? (
                <Loader2 className="mr-2 size-4 animate-spin" />
              ) : (
                <Trash2 className="mr-2 size-4" />
              )}
              {t("avatar.delete")}
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
