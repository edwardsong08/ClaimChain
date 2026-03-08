import "./globals.css";
import type { Metadata } from "next";
import Providers from "@/components/providers";
import Navbar from "@/components/navbar";

export const metadata: Metadata = {
  title: "ClaimChain",
  description: "ClaimChain frontend",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>
        <Providers>
          <Navbar />
          {children}
        </Providers>
      </body>
    </html>
  );
}