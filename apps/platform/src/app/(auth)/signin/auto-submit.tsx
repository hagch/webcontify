"use client";

import { useEffect, useRef } from "react";

export function AutoSubmit() {
  const formRef = useRef<HTMLFormElement | null>(null);
  const submitted = useRef(false);

  useEffect(() => {
    if (submitted.current) return;
    submitted.current = true;
    const form = document.forms[0];
    if (form) {
      form.requestSubmit();
    }
  }, []);

  return null;
}
