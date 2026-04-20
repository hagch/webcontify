const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://api.localhost";

export const customInstance = async <T>(
  url: string,
  options?: RequestInit
): Promise<T> => {
  const response = await fetch(`${BASE_URL}${url}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options?.headers,
    },
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => null);
    throw {
      data: errorData,
      status: response.status,
      headers: response.headers,
    };
  }

  const data =
    response.status === 204 ? undefined : await response.json();

  return { data, status: response.status, headers: response.headers } as T;
};
