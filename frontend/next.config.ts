import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/backend-api/:path*",
        destination: "http://18.222.221.138/:path*",
      },
    ];
  },
};

export default nextConfig;
